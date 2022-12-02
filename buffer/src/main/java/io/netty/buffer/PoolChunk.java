/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Description of algorithm for PageRun/PoolSubpage allocation from PoolChunk
 *
 * Notation: The following terms are important to understand the code
 * > page  - a page is the smallest unit of memory chunk that can be allocated
 * > chunk - a chunk is a collection of pages
 * > in this code chunkSize = 2^{maxOrder} * pageSize
 *
 * To begin we allocate a byte array of size = chunkSize
 * Whenever a ByteBuf of given size needs to be created we search for the first position
 * in the byte array that has enough empty space to accommodate the requested size and
 * return a (long) handle that encodes this offset information, (this memory segment is then
 * marked as reserved so it is always used by exactly one ByteBuf and no more)
 *
 * For simplicity all sizes are normalized according to PoolArena#normalizeCapacity method
 * This ensures that when we request for memory segments of size >= pageSize the normalizedCapacity
 * equals the next nearest power of 2
 *
 * To search for the first offset in chunk that has at least requested size available we construct a
 * complete balanced binary tree and store it in an array (just like heaps) - memoryMap
 *
 * The tree looks like this (the size of each node being mentioned in the parenthesis)
 *
 * depth=0        1 node (chunkSize)
 * depth=1        2 nodes (chunkSize/2)
 * ..
 * ..
 * depth=d        2^d nodes (chunkSize/2^d)
 * ..
 * depth=maxOrder 2^maxOrder nodes (chunkSize/2^{maxOrder} = pageSize)
 *
 * depth=maxOrder is the last level and the leafs consist of pages
 *
 * With this tree available searching in chunkArray translates like this:
 * To allocate a memory segment of size chunkSize/2^k we search for the first node (from left) at height k
 * which is unused
 *
 * Algorithm:
 * ----------
 * Encode the tree in memoryMap with the notation
 *   memoryMap[id] = x => in the subtree rooted at id, the first node that is free to be allocated
 *   is at depth x (counted from depth=0) i.e., at depths [depth_of_id, x), there is no node that is free
 *
 *  As we allocate & free nodes, we update values stored in memoryMap so that the property is maintained
 *
 * Initialization -
 *   In the beginning we construct the memoryMap array by storing the depth of a node at each node
 *     i.e., memoryMap[id] = depth_of_id
 *
 * Observations:
 * -------------
 * 1) memoryMap[id] = depth_of_id  => it is free / unallocated
 * 2) memoryMap[id] > depth_of_id  => at least one of its child nodes is allocated, so we cannot allocate it, but
 *                                    some of its children can still be allocated based on their availability
 * 3) memoryMap[id] = maxOrder + 1 => the node is fully allocated & thus none of its children can be allocated, it
 *                                    is thus marked as unusable
 *
 * Algorithm: [allocateNode(d) => we want to find the first node (from left) at height h that can be allocated]
 * ----------
 * 1) start at root (i.e., depth = 0 or id = 1)
 * 2) if memoryMap[1] > d => cannot be allocated from this chunk
 * 3) if left node value <= h; we can allocate from left subtree so move to left and repeat until found
 * 4) else try in right subtree
 *
 * Algorithm: [allocateRun(size)]
 * ----------
 * 1) Compute d = log_2(chunkSize/size)
 * 2) Return allocateNode(d)
 *
 * Algorithm: [allocateSubpage(size)]
 * ----------
 * 1) use allocateNode(maxOrder) to find an empty (i.e., unused) leaf (i.e., page)
 * 2) use this handle to construct the PoolSubpage object or if it already exists just call init(normCapacity)
 *    note that this PoolSubpage object is added to subpagesPool in the PoolArena when we init() it
 *
 * Note:
 * -----
 * In the implementation for improving cache coherence,
 * we store 2 pieces of information depth_of_id and x as two byte values in memoryMap and depthMap respectively
 *
 * memoryMap[id]= depth_of_id  is defined above
 * depthMap[id]= x  indicates that the first node which is free to be allocated is at depth x (from root)
 * PoolChunk 分配大于或等于8KB的内存
 *
 * Netty底层的内存分配和管理主要由PoolChunk实现，大于16MB的 PoolChunk由于不放入内存池管理，比较简单，这里不进行过多的讲 解。
 * 下面主要讲解PoolChunk为何能管理内存，以及它具有的重要属 性。
 *
 * 可以把Chunk看作一块大的内存，这块内存被分成了很多小块的内 存，Netty在使用内存时，会用到其中一块或多块小内存。如图6-3所 示，通过0和1来标识每位的占用情况
 * ，通过内存的偏移量和请求内存 空间大小reqCapacity来决定读/写指针。在图6-3中，浅灰色部分表示 内存实际用到的部分，深灰色部分表示浪费的内存。内存池在分配内
 * 存时，只会预先准备固定大小和数量的内存块，不会请求多少内存就 恰好给多少内存，因此会有一定的内存被浪费。使用完后交还给 PoolChunk并还原，以便重复使用。
 *
 *  PoolChunk内部维护了一棵平衡二叉树，默认由2048个page组成， 一个page默认为8KB，整个Chunk默认为16MB，其二叉树结构如图6-4所 示。
 *
 *  当分配的内存大于2^13B(8196B)时，可以通过内存值计算对应 的层级:int d=11-(log2(normCapacity)-13)。其中，normCapacity 为分配的内存大小，
 *  它大于或等于8KB且为8KB的整数倍。例如，申请 大小为16KB的内存，d=11-(14-13)=10，表示只能在小于或等于10层上 寻找还未被分配的节点。
 *
 */
final class PoolChunk<T> implements PoolChunkMetric {

    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    final PoolArena<T> arena;
    final T memory;
    final boolean unpooled;
    final int offset;
    // 在PoolChunk中，用一个数组memoryMap维护了所有节点(节点数 为1~2048×2-1)及其对应的高度值。memoryMap是在PoolChunk初始 化时构建的，
    // 其下标为图6-4中节点的位置，其值为节点的高度，如 memoryMap[1]=0 、 memoryMap[2]=1 、 memoryMap[2048]=11 、 memoryMap[4091]=11
    private final byte[] memoryMap;
    // 除memoryMap之外，还有一个同样的数组——depthMap。两 者的区别是:depthMap一直不会改变，通过depthMap可以获取节点的 内存大小，还可以获取节点的
    // 初始高度值;而memoryMap的节点和父节 点对应的高度值会随着节点内存的分配发生变化。当节点被全部分配 完时，它的高度值会变成12，表示
    // 目前已被占用，不可再被分配，并 且会循环递归地更新其上所有父节点的高度值，高度值都会加1，如图 6-5所示。
    private final byte[] depthMap;
    private final PoolSubpage<T>[] subpages;
    /** Used to determine if the requested capacity is equal to or greater than pageSize. */
    private final int subpageOverflowMask;
    private final int pageSize;
    private final int pageShifts;
    private final int maxOrder;
    private final int chunkSize;
    private final int log2ChunkSize;
    private final int maxSubpageAllocs;
    /** Used to mark memory as unusable */
    private final byte unusable;

    // Use as cache for ByteBuffer created from the memory. These are just duplicates and so are only a container
    // around the memory itself. These are often needed for operations within the Pooled*ByteBuf and so
    // may produce extra GC, which can be greatly reduced by caching the duplicates.
    //
    // This may be null if the PoolChunk is unpooled as pooling the ByteBuffer instances does not make any sense here.
    private final Deque<ByteBuffer> cachedNioBuffers;

    private int freeBytes;

    PoolChunkList<T> parent;
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize, int offset) {
        unpooled = false;
        this.arena = arena;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.maxOrder = maxOrder;
        this.chunkSize = chunkSize;
        this.offset = offset;
        unusable = (byte) (maxOrder + 1);
        log2ChunkSize = log2(chunkSize);
        subpageOverflowMask = ~(pageSize - 1);
        freeBytes = chunkSize;

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        maxSubpageAllocs = 1 << maxOrder;

        // Generate the memory map.
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++ d) { // move down the tree one level at a time
            int depth = 1 << d;
            for (int p = 0; p < depth; ++ p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex ++;
            }
        }

        subpages = newSubpageArray(maxSubpageAllocs);
        cachedNioBuffers = new ArrayDeque<ByteBuffer>(8);
    }

    /** Creates a special chunk that is not pooled. */
    PoolChunk(PoolArena<T> arena, T memory, int size, int offset) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        this.offset = offset;
        memoryMap = null;
        depthMap = null;
        subpages = null;
        subpageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        maxOrder = 0;
        unusable = (byte) (maxOrder + 1);
        chunkSize = size;
        log2ChunkSize = log2(chunkSize);
        maxSubpageAllocs = 0;
        cachedNioBuffers = null;
    }

    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }

    @Override
    public int usage() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }
        return usage(freeBytes);
    }

    private int usage(int freeBytes) {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }


    // PoolByteBuf通过PoolSubpage分配内存后返回的指针来获取偏移 量，通过偏移量可以操作内存块的读/写，但还缺少对PoolByteBuf的 初始化、
    // 指针handle如何转换成偏移量offset、PoolByteBuf与 PoolSubpage的关联的介绍。
    // 通过前面对PoolChunk的allocateSubpage()方法进行的剖析，并 结合本小节PoolSubpage的allocateSubpage()方法的解读，对于小于 8KB内存

    // 的第一次分配有了深入的了解，PoolSubpage与PoolByteBuf的 关联还需要回到PoolChunk的allocate()方法。指针handle的高32位存 储PoolSubpage
    // 中分配的内存段在page中的相对偏移量;低32位存储 page在PoolChunk的二叉树中的位置memoryMapIdx，通过这个位置获取 其偏移量，两个偏移量相加
    // 就是PoolByteBuf的偏移量，PoolByteBuf 运用偏移量可操作读/写索引，实现数据的读/写。具体代码解读如 下:
    // 内存具体分配方法
    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        final long handle;
        // 内存指针，分配的二叉树内存节点偏移量或page和PoolSubpage的偏移量
        if ((normCapacity & subpageOverflowMask) != 0) { // >= pageSize  分配大于或等于page 的内存
            handle =  allocateRun(normCapacity);                           // 具体分配的内存节点的偏移量
        } else {
            handle = allocateSubpage(normCapacity);                         // page 和PoolSubpage的偏移量
        }

        if (handle < 0) {                                   // 分配失败
            return false;
        }
        // 从缓存的ByteBuffer对象池中获取一个ByteBuffer对象，有可能为null
        ByteBuffer nioBuffer = cachedNioBuffers != null ? cachedNioBuffers.pollLast() : null;
        // 初始化申请到的内存数据，并对PoolByteBuf对象进行初始化
        initBuf(buf, nioBuffer, handle, reqCapacity);
        return true;
    }

    /**
     * Update method used by allocate
     * This is triggered only when a successor is allocated and all its predecessors
     * need to update their state
     * The minimal depth at which subtree rooted at id has some free space
     *
     * @param id id
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            byte val = val1 < val2 ? val1 : val2;
            setValue(parentId, val);
            id = parentId;
        }
    }

    /**
     * Update method used by free
     * This needs to handle the special case when both children are completely free
     * in which case parent be directly allocated on request of size = child-size * 2
     *
     * @param id id
     */
    private void updateParentsFree(int id) {
        int logChild = depth(id) + 1;                       // 节点的值
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);              // 相邻节点
            // 第一次循环时与id 对应的高度值一样， 后续都要减1，表示子节点的值
            logChild -= 1; // in first iteration equals log, subsequently reduce 1 from logChild as we traverse up

            if (val1 == logChild && val2 == logChild) {                 // 若两个子节点都可分配，则该节点变回自己所在层的depth，表示该节点也可完全被分配
                setValue(parentId, (byte) (logChild - 1));
            } else {
                byte val = val1 < val2 ? val1 : val2;
                setValue(parentId, val);        // 设置父节点的调试值为两个子节点的最小值
            }

            id = parentId;
        }
    }

    /**
     * Algorithm to allocate an index in memoryMap when we query for a free node
     * at depth d
     *
     * @param d depth
     * @return index in memoryMap
     * Netty源码是如何查找对应的可用节点并更新其父节点的高度值的 呢?
     * Netty采用了前序遍历算法，从根节点开始，第二层为左右节点， 先看左边节点内存是否够分配，若不够，则选择其兄弟节点(右节 点);
     * 若当前左节点够分配，则需要继续向下一层层地查找，直到找 到层级最接近d(分配的内存在二叉树中对应的层级)的节点。具体查 找算法如下:
     * d 是申请的内存在PoolChunk 二叉树中的调试值，若内存为8KB,则d为11
     */
    private int allocateNode(int d) {
        int id = 1;

        int initial = - (1 << d); // has last d bits = 0 and rest all = 1         掩码，与id进行与操作后，若> 0 ，则说明id 对应的调试大于或等于d
        byte val = value(id);                            // 为memoryMap[id]
        if (val > d) { // unusable                       // 若当前分配的空间无法满足要求 ， 则直接返回-1，分配失败
            return -1;
        }
        // 有空间可以分配了，就需要一步一步的找到更接近调试值的d的节点，若找到的调试值等于d, 但此时其下标与initial进行与操作后的0 ,则说明
        // 其子节点有一个未被分配，且其初始化层级 < d ,只是由于其有一个节点被分配了，所以层级val 与 d 相等
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
            id <<= 1;                           // 每次都需要把id 向 下移动一层， 即左移一位
            val = value(id);                    // 获取id 对应的层级高度值值memoryMap[id]
            if (val > d) {                      // 若id对应的层级调试值大于d, 则此时去其兄弟节点找，肯定能找到
                id ^= 1;                        // 获取其兄弟节点，兄弟节点的位置通过id值异或1得到
                val = value(id);                // 获取其兄弟节点的高度值
            }
        }

        byte value = value(id);                 // 获取找到节点的高度值
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);
        setValue(id, unusable);                 // mark as unusable 标识为不可用
        updateParentsAlloc(id);                 // 返回id(1~2048*2-1) 通过id 可以获取其层级调试值，也可以算出其占用的内存空间的大小
        return id;
    }

    //  通过上述图、文字及代码，描述了Netty在分配[8KB,16MB]内存时 是采用怎样的方式快速找到对应可用的内存块的id的，此id为 PoolChunk的
    //  内存映射属性memoryMap的下标，通过此下标可以找到其 偏移量。获取偏移量的算法为:int shift=id^1<<depth(id);int offset=shift*runLength(id);。
    //  主要通过id异或与id同层级最左边的 元素的下标值得到偏移量，再用偏移量乘以当前层级节点的内存大 小，进而获取在PoolChunk整个内存中的偏移量。有了偏移量和需要分
    // 配的内存大小Length，以及最大可分配内存的大小(可根据 runLength(id)计算得出)，即可初始化PooledByteBuf，完成内存分 配。runLength()方法为return 1<<24-depth(id)，
    // 其中id的层级越 大，返回的值越小，当id为0时，返回16MB，为整个PoolChunk内存的 大小;当id为11时，返回8KB，为page默认的大小。

    // 肯定有读者会感到疑惑:若需要分配的内存不在memoryMap数组里 该怎么办呢?此时无法通过int d=11-(log2(normCapacity)-13)来计 算。然而在内存具体分配之前，
    // Netty对需要的内存量进行了加工处理 (会根据其大小进行处理)，若需要的内存大于或等于512B，则此时 计算一个大于且距它最近的2的指数次幂的值，这个值就是
    // PooledByteBuf的最大可分配内存。若小于512B，则找到距 reqCapacity最近的下一个16的倍数的值。

    // 大于或等于page的内存分配基本上讲完了，已详细了解了其内存 的具体分配算法。但在实际应用中，一般接收的请求信息都不大，
    // 很 多时候请求数据不会超过8KB。在小于8KB的情况下，Netty的内存分配 又是怎样的呢?下面通过处理PoolSubpage的逻辑来解决这个问题。

    /**
     * Allocate a run of pages (>=1)
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateRun(int normCapacity) {
        int d = maxOrder - (log2(normCapacity) - pageShifts);
        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);
        return id;
    }

    /**
     * Create / initialize a new PoolSubpage of normCapacity
     * Any PoolSubpage created / initialized here is added to subpage pool in the PoolArena that owns this PoolChunk
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     * 6.2.1小节详细了解了PoolChunk分配大于或等于8KB的内存，那么 对于内存小于8KB的情况，Netty又是如何设计的呢?PoolChunk有一个 PoolSubpage[]组数，
     * 叫subpages，它的数组长度与PoolChunk的page 节点数一致，都是2048，由于它只分配小于8KB的数据，且PoolChunk 的每个page节点只能分配一种PoolSubpage，
     * 因此subpages的下标与 PoolChunk的page节点的偏移位置是一一对应的。PoolChunk上的每个 page均可分配一个PoolSubpage链表，由于链表中元素的大小都相同，
     * 因此这个链表最大的元素个数也已经确定了，链表的头节点为 PoolSubpage[]组数中的元素。PoolChunk分配PoolSubpage的步骤如 下。
     *
     * 步骤1:在PoolChunk的二叉树上找到匹配的节点，由于小于8KB， 因此只匹配page节点。因为page节点从2048开始，所以page将节点与
     * 2048进行异或操作就可以得到PoolSubpage[]数组subpages的下标 subpageIdx。
     * 步骤2:取出subpages[subpageIdx]的值。若值为空，则新建一个 PoolSubpage并初始化。
     * 步骤3:PoolSubpage根据内存大小分两种，即小于512B为tiny， 大于或等于512B且小于8KB为small，此时PoolSubpage还未区分是 small还是tiny。
     * 然后把PoolSubpage加入PoolArena的PoolSubpage缓 存池中，以便后续直接从缓存池中获取。
     *
     * 步骤4:PoolArena的PoolSubpage[]数组缓存池有两种，分别是存 储 (0,512) 个 字 节 的 tinySubpagePools 和 存 储 [512,8192) 个
     * 字 节 的 smallSubpagePools。由于所有内存分配大小elemSize都会经过 normalizeCapacity的处理，因此当elemSize>=512时，elemSize会成 倍 增 长 ，
     * 即 512→1024→2048→4096→8192; 当 elemSize<512 时 ， elemSize从16开始，每次加16B，elemSize的变化规律与 tinySubpagePools和smallSubpagePools两个数组的存储原理是一致 的。
     *
     * tinySubpagePools从16开始存储，根据下标值加16B，因此其数组 长度为512/16B=32B。smallSubpagePools从512开始，根据下标值成倍 地增长。数组总共有4个元素。通过elemSize可以快速找到合适的 poolSubpage。
     * tinysubpage通过elemSize>>>4即可获取tinysubpage[]数组的下 标tableIdx，而smallSubpage则需要除1024看是否为0，若不为0，则 通过循环除以2再看是否为0，最终找到tableIdx。有了tableIdx就可
     * 以获取对应的PoolSubpage链表，若PoolSubpage链表获取失败，或链 表中不可再分配元素，则回到步骤1，从PoolChunk中重新分配;否则 直接获取链表中可分配的元素。
     * 根据以上逻辑分析，可以用图形方式来描述PoolChunk中page与 PoolSubpage和Arena中两个PoolSubpages的对应关系，如图6-6所示。
     *
     *
     *
     * PoolChunk分配小于8KB内存的具体代码解读如下:
     *
     *  当申请内存小于8KB时，此方法被调用
     */
    private long allocateSubpage(int normCapacity) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        // 通过优化后的内存容量找到Areana的两个subpages缓存池其中 一个对应的空间head指针
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);
        int d = maxOrder; // subpages are only be allocated from pages i.e., leaves 小于8KB 内存只在11层分配
        // 由于分配前需要把PoolSubpage加入缓存池中，以便一回直接从Arean的缓存池中获取，因此选择加锁head指针
        synchronized (head) {
            int id = allocateNode(d);                   // 获取一个可用的节点
            if (id < 0) {
                return id;
            }

            final PoolSubpage<T>[] subpages = this.subpages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;              // 可用空间减去一个page，表示此page被占用

            int subpageIdx = subpageIdx(id);            // 根据page的偏移值减2048获取PoolSubpage的索引
            PoolSubpage<T> subpage = subpages[subpageIdx];              // 获取page对应的PoolSubpage
            if (subpage == null) {  // 若为空，则初始化一个，初始化会运行PoolSubpage的addToPool()方法，把subpage追加到head的后面
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                subpages[subpageIdx] = subpage;
            } else {
                subpage.init(head, normCapacity);           // 初始化同样会调用addToPool()方法，此处思考，什么情况下才会发生这类情况
            }
            return subpage.allocate();                      // PoolSubpage 的内存分配
        }
    }

    /**
     * Free a subpage or a run of pages
     * When a subpage is freed from PoolSubpage, it might be added back to subpage pool of the owning PoolArena
     * If the subpage pool in PoolArena has at least one other PoolSubpage of given elemSize, we can
     * completely free the owning Page so it is available for subsequent allocations
     *
     * @param handle handle to free
     *
     *
     */
    // 内存的释放相对其分配来说要简单很多，下面主要剖析PoolChunk 和PoolSubpage的释放。当内存释放时，同样先根据handle指针找到内 存在PoolChunk和PoolSubpage中的相对偏移量，具体释放步骤如下。
    // 1. 若在PoolSubpage上的偏移量大于0，则交给PoolSubpage去 释放，这与PoolSubpage内存申请有些相似，根据PoolSubpage内存分 配段的偏移位
    // bitmapIdx找到long[]数组bitmap的索引q，将bitmap[q] 的具体内存占用位r置为0(表示释放)。同时调整Arena中的 PoolSubpage缓存池，
    // 若PoolSubpage已全部释放了，且池中除了它还 有其他节点，则从池中移除;若由于之前PoolSubpage的内存段全部分 配完并从池中移除过，
    // 则在其当前可用内存段numAvail等于-1且 PoolSubpage释放后，对可用内存段进行“++”运算，从而使 numAvail++等于0，此时会把释放
    // 的PoolSubpage追加到Arena的 PoolSubpage缓存池中，方便下次直接从缓冲池中获取。
    // 2. 若在PoolSubpage上的偏移量等于0，或者PoolSubpage释放 完后返回false(PoolSubpage已全部释放完，同时从Arena的 PoolSubpage缓存池中移除了)，
    // 则只需更新PoolChunk二叉树对应节 点的高度值，并更新其所有父节点的高度值及可用字节数即可。
    //
    void free(long handle, ByteBuffer nioBuffer) {
        int memoryMapIdx = memoryMapIdx(handle);
        int bitmapIdx = bitmapIdx(handle);

        if (bitmapIdx != 0) { // free a subpage            // 先释放subpage
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;

            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure
            // 与分配时一样， 先去Area池中找到subpage对应的head指针
            PoolSubpage<T> head = arena.findSubpagePoolHead(subpage.elemSize);
            synchronized (head) {
                // 获取32位bitmapIdx次给PoolSubpage释放，释放后返回true,不再继续释放
                if (subpage.free(head, bitmapIdx & 0x3FFFFFFF)) {
                    return;
                }
            }
        }
        freeBytes += runLength(memoryMapIdx);                       // 释放的字节数调整
        setValue(memoryMapIdx, depth(memoryMapIdx));                  // 设置节点值为节点初始化值，depth()方法使用的是byte[] depthMap，此字节初始化后就不再改变了
        updateParentsFree(memoryMapIdx);                        // 更新父亲节点的高度值

        if (nioBuffer != null && cachedNioBuffers != null &&                // 把nioBuffer放入到缓存队列中，以便下次再直接使用
                cachedNioBuffers.size() < PooledByteBufAllocator.DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK) {
            cachedNioBuffers.offer(nioBuffer);
        }
    }

    void initBuf(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        int memoryMapIdx = memoryMapIdx(handle);                        // 用int强转， 取handle的低32位， 低32位存储的是二叉树节点的位置
        int bitmapIdx = bitmapIdx(handle);                              // 右移32位并强制转为int 型 ，相当于获取handle的高32位， 即PoolSubpage的内存段相对page的偏移量
        if (bitmapIdx == 0) {                                           // 无PoolSubpage，即大于或等于page的内存分配
            byte val = value(memoryMapIdx);                             // 获取节点的高度
            assert val == unusable : String.valueOf(val);               // 判断节点的高度是不可用的（默认为12），
            buf.init(this, nioBuffer, handle, runOffset(memoryMapIdx) + offset,
                    reqCapacity, runLength(memoryMapIdx), arena.parent.threadCache());          // 计算偏移量，offset的值为内存对齐偏移量
        } else {
            initBufWithSubpage(buf, nioBuffer, handle, bitmapIdx, reqCapacity);
        }
    }

    void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        initBufWithSubpage(buf, nioBuffer, handle, bitmapIdx(handle), reqCapacity);
    }

    private void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer,
                                    long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = memoryMapIdx(handle);

        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        buf.init(                       //根据page相对chunk的偏移量+PoolSubpage相对page的偏移量+对外内存地址对齐偏移量，初始化PoolByteBuf
            this, nioBuffer, handle,
            runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize + offset,
                reqCapacity, subpage.elemSize, arena.parent.threadCache());
    }

    private byte value(int id) {
        return memoryMap[id];
    }

    private void setValue(int id, byte val) {
        memoryMap[id] = val;
    }

    private byte depth(int id) {
        return depthMap[id];
    }

    private static int log2(int val) {
        // compute the (0-based, with lsb = 0) position of highest set bit i.e, log2
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        return 1 << log2ChunkSize - depth(id);
    }

    // 6.2 节讲过获取偏移量的算法
    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk
        int shift = id ^ 1 << depth(id);
        return shift * runLength(id);
    }

    private int subpageIdx(int memoryMapIdx) {
        return memoryMapIdx ^ maxSubpageAllocs; // remove highest set bit, to get offset
    }

    private static int memoryMapIdx(long handle) {
        return (int) handle;
    }

    private static int bitmapIdx(long handle) {
        return (int) (handle >>> Integer.SIZE);
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public int freeBytes() {
        synchronized (arena) {
            return freeBytes;
        }
    }

    @Override
    public String toString() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }

        return new StringBuilder()
                .append("Chunk(")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append(": ")
                .append(usage(freeBytes))
                .append("%, ")
                .append(chunkSize - freeBytes)
                .append('/')
                .append(chunkSize)
                .append(')')
                .toString();
    }

    void destroy() {
        arena.destroyChunk(this);
    }
}
