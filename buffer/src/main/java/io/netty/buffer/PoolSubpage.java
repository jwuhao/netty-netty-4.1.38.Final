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

/***
 * PoolSubpage是由PoolChunk的page生成的，page可以生成多种 PoolSubpage，但一个page只能生成其中一种PoolSubpage。 PoolSubpage可以分为很多段，
 * 每段的大小相同，且由申请的内存大小 决定。在讲解PoolSubpage具体分配内存之前，先看看它的重要属性
 *
 *
 * 由于PoolSubpage每段的最小值为16B，因此它的段的总数量最多 为pageSize/16。把PoolSubpage中每段的内存使用情况用一个long[] 数组来标识，
 * long类型的存储位数最大为64B，每一位用0表示为空闲 状态，用1表示被占用，这个数组的长度为pageSize/16/64B，默认情 况下，long数组的长度
 * 最大为8192/16/64B=8B。每次在分配内存时， 只需查找可分配二进制的位置，即可找到内存在page中的相对偏移 量，图6-7为PoolSubpag的内存段分配实例。
 *
 */
final class PoolSubpage<T> implements PoolSubpageMetric {

    final PoolChunk<T> chunk;       // 当前分配内存的chunk,表明该subpage属于哪一个Chunk
    // 当前page在chunk的memoryMap中的下标 id,  表明该subpage在二叉树的节点编号，由于subpage是由Page
    // 转换而来，而Page都在二叉树的最后一层，因此这个值一定在2048~4095之间
    private final int memoryMapIdx;
    private final int runOffset;    // 当page 在chunk的memory上的偏移量
    private final int pageSize;     // page的大小 ，默认是8192b ,也就是8K
    private final long[] bitmap;    // poolSubpage每段内存的占用状态，采用二进制位来标识 ,用于标记element是否可用

    PoolSubpage<T> prev;            // 指向前一个PoolSubpage
    PoolSubpage<T> next;            // 指向后一个PoolSubpage

    boolean doNotDestroy;
    // Page 转换为subpage后，每个element的大小，在上个例子中， 这个值为32，elemSize决定了这个subpage在tinySubpagePools或
    // smallSubpagePools数组中的位置
    int elemSize;
    private int maxNumElems;        // 这个subpage有多少个element, 这个段等于8K/elmSize
    private int bitmapLength;       // 实际采用二进制位标识的long数组的长度值，根据每段大小elementSize 和pageSize业计算得来的
    private int nextAvail;          // 下一个可用的element位置
    private int numAvail;           // 可用的element的数量

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /** Special constructor that creates a linked list head */
    PoolSubpage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        runOffset = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.runOffset = runOffset;
        this.pageSize = pageSize;
        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64  等价于  2 ^ 13 / 2 ^ 4 / 2 ^ 6 = 2 ^ 3 = 8
        init(head, elemSize);
    }

    void init(PoolSubpage<T> head, int elemSize) {
        doNotDestroy = true;
        this.elemSize = elemSize;   // 每个element的大小
        if (elemSize != 0) {
            maxNumElems = numAvail = pageSize / elemSize; // numAvail， 可用element的数量，maxNumElems 最大element数量
            nextAvail = 0;          // 初始化时，下一个可用element的位置
            //  maxNumElems >>> 6 等价于 最大element数量 / 64，每一个long类型有64位
            //  bitmapLength 表示需要多少个long值来标识element是否被使用
            bitmapLength = maxNumElems >>> 6;
            // 63对应的二进制数为 0000 0000 0000 0000 0000 0000 0011 1111
            if ((maxNumElems & 63) != 0) {  // 如果 maxNumElems & 63 != 0
                bitmapLength ++;
            }
            for (int i = 0; i < bitmapLength; i ++) {
                bitmap[i] = 0;
            }
        }
        addToPool(head);
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     * 由于PoolSubpage每段的最小值为16B，因此它的段的总数量最多 为pageSize/16。把PoolSubpage中每段的内存使用情况用一个long[] 数组来标识，
     * long类型的存储位数最大为64 ，每一位用0表示为空闲 状态，用1表示被占用，这个数组的长度为pageSize/16/64 ，默认情 况下，
     * long数组的长度最大为8192/16/64  =8 。每次在分配内存时， 只需查找可分配二进制的位置，即可找到内存在page中的相对偏移 量，
     * 图6-7为PoolSubpage 的内存段分配实例。
     */
    // PoolSubpage 通过位图bitmap记录每个内存块是否已经被使用，在上述的示例中， 8K / 32 = 256 ，因为每个long有64位，所以需要256 / 64 = 4 个long
    // 类型的即可描述全部的内存分配状态，因此bitmap数组的长度为4， 从bitmap[0] 开始记录， 每分配一个内存块，就会移动到bitmap[0] 中的下一个二进制位。
    // 直至bitmap[0] 的所有的二进制都赋值为1， 然后继续分配 bitmap[1]，以此类推， 当我们用2049节点进行分配内存时，bitmap[0]中的二进制位如下图所示 :


    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }

        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        final int bitmapIdx = getNextAvail();// 获取PoolSubpage下一个可用的位置
        int q = bitmapIdx >>> 6;             // 获取该位置的bitmap数组对应的下标值，右移6位  2 ^ 6  = 64
        int r = bitmapIdx & 63;              // 获取bitmap[q]上实际可用的位，63的二进制表示为：0011 1111
        assert (bitmap[q] >>> r & 1) == 0;   // 确定该位没有被占用
        bitmap[q] |= 1L << r;                // 将该位设为1，表示已经被占用，此处 1L << r 表示将r 设置为1

        if (-- numAvail == 0) {              // 若没有可用的段，则说明此page/PoolSubpage已经分配满了， 没有必要oxxfyt到PoolArena池，应该从Pool 中移除
            removeFromPool();
        }

        return toHandle(bitmapIdx);          // 把当前page的索引和PoolSubPage 的索引一起返回低32位表示page的index，高32位表示PoolSubPage的index
    }

    /**
     * @return {@code true} if this subpage is in use.
     *         {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     */
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        int q = bitmapIdx >>> 6;                            // 由于long型 是64位，因此除了64 就是long[] bitmap的下标
        int r = bitmapIdx & 63;                             // 找到bitmap[q]对应的位置
        assert (bitmap[q] >>> r & 1) != 0;                  // 判断当前位是否为已经分配状态
        bitmap[q] ^= 1L << r;                               // 把bitmap[q] 的 r 位设置为0，表示未分配

        setNextAvail(bitmapIdx);                            // 将该位置设置为下一个可用的位置，这也是在分配时会发生nextAvail大于0的情况

        if (numAvail ++ == 0) {         // 若之前没有可分配的内存，从池中移除了， 则将PoolSubpage继续添加到Arena的缓存池中，以便下回分配
            addToPool(head);
            return true;
        }
        // 若还没有被释放的内存，则直接返回
        if (numAvail != maxNumElems) {
            return true;
        } else {
            // Subpage not in use (numAvail == maxNumElems)  若内存全部被释放了，且池中没有其他的PoolSubpage，则不从池中移除，直接返回
            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                return true;
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            doNotDestroy = false;               // 若逊尼中还有其他的节点，且当前节点内存已经全部被释放，则从池中移除，并返回false , 对其上的page也会进行相应的回收
            removeFromPool();
            return false;
        }
    }

    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;   // this.prev = head
        next = head.next; // this.next = head.next
        next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    private int getNextAvail() {                // 查找下一个可用的位置
        int nextAvail = this.nextAvail;         // 若下一个可用的位置大于或等于0 ， 则说明是每一次分配或正好已经有内存回收，可直接返回
        if (nextAvail >= 0) {
            this.nextAvail = -1;                // 每次分配完内存后，都要将nextAvail 设置为-1
            return nextAvail;
        }
        // 没有直接可用内存，需要继续查找
        return findNextAvail();
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        // 遍历用来标识内存是否被占用的数组
        for (int i = 0; i < bitmapLength; i ++) {
            long bits = bitmap[i];
            // 若当前long型的标识位不全为1，则表示其中有未被使用的内存
            if (~bits != 0) {
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        // i 表示 bitMap的位置，由于bitmap 每个值有64位 ， 因此用i * 64 来表示bitmap[i]的PoolSubpage中的偏移量
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j ++) {
            if ((bits & 1) == 0) {                      // 判断第一位是否为0，为0表示该位空闲
                int val = baseVal | j;
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;                                // 若bits的第一位不为0 ， 则继续右移一位， 判断第二位
        }
        return -1;                              // 如果没有找到，则返回-1
    }

    // 反当前page的索引和PoolSubpage的索引一起返回 , 低32位表示page的index ，高32位表示Poolsubpage的index
    private long toHandle(int bitmapIdx) {
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }

    @Override
    public String toString() {
        final boolean doNotDestroy;
        final int maxNumElems;
        final int numAvail;
        final int elemSize;
        if (chunk == null) {
            // This is the head so there is no need to synchronize at all as these never change.
            doNotDestroy = true;
            maxNumElems = 0;
            numAvail = 0;
            elemSize = -1;
        } else {
            synchronized (chunk.arena) {
                if (!this.doNotDestroy) {
                    doNotDestroy = false;
                    // Not used for creating the String.
                    maxNumElems = numAvail = elemSize = -1;
                } else {
                    doNotDestroy = true;
                    maxNumElems = this.maxNumElems;
                    numAvail = this.numAvail;
                    elemSize = this.elemSize;
                }
            }
        }

        if (!doNotDestroy) {
            return "(" + memoryMapIdx + ": not in use)";
        }

        return "(" + memoryMapIdx + ": " + (maxNumElems - numAvail) + '/' + maxNumElems +
                ", offset: " + runOffset + ", length: " + pageSize + ", elemSize: " + elemSize + ')';
    }

    @Override
    public int maxNumElements() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }

        synchronized (chunk.arena) {
            return maxNumElems;
        }
    }

    @Override
    public int numAvailable() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }

        synchronized (chunk.arena) {
            return numAvail;
        }
    }

    @Override
    public int elementSize() {
        if (chunk == null) {
            // It's the head.
            return -1;
        }

        synchronized (chunk.arena) {
            return elemSize;
        }
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    void destroy() {
        if (chunk != null) {
            chunk.destroy();
        }
    }
}
