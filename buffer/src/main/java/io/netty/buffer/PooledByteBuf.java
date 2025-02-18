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

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * 下面介绍一个非常重要的ByteBuf抽象类——PooledByteBuf。这 个类继承于AbstractReference CountedByteBuf，其对象主要由内存 池分配器
 * PooledByteBufAllocator创建。比较常用的实现类有两种: 一种是基于堆外直接内存池构建的PooledDirectByteBuf，是Netty在 进行I/O的读/写
 * 时的内存分配的默认方式，堆外直接内存可以减少内 存数据拷贝的次数;另一种是基于堆内内存池构建的 PooledHeapByteBuf。
 *
 *
 * 除了上述两种实现类，Netty还使用Java的后门类 sun.misc.Unsafe实现了两个缓冲区，即PooledUnsafeDirectByteBuf 和PooledUnsafeHeapByteBuf。
 * 这个强大的后门类会暴露对象的底层地 址，一般不建议使用，Netty为了优化性能引入了Unsafe。
 *
 *
 * 由于创建PooledByteBuf对象的开销大，而且在高并发情况下，当 网络I/O进行读/写时会创建大量的实例。因此，为了降低系统开销，
 * Netty对Buffer对象进行了池化，缓存了Buffer对象，使对此类型的 Buffer可进行重复利用。PooledByteBuf是从内存池中分配出来的 Buffer，
 * 因此它需要包含内存池的相关信息，如内存块Chunk、 PooledByteBuf在内存块中的位置及其本身所占空间的大小等。图4-11 描述了PooledByteBuf
 * 的核心功能和属性。接下来对这些功能的源码进 行详细的解读。
 *                                         |-------->对象池------recyclerHandle----------------> 对象重复利用无须每次创建
 *                                         |
 *                                         |
 *                                         |                            |-------> chunk -------> 一块大的内存区域
 *                                         |                            |-------> memory-------> chunk中具体的缓存空间
 *                     |------> 属性------>|-------> 内存池相关--------> |-------> bandle ------> 定位到chunk中的一块连接内存的指针
 *                     |                   |                            |-------> offset ------> 偏移量
 *                     |                   |                            |-------> length ------> 长度（ByteBuffer中的可读字节数）
 *                     |                   |                            |-------> 最大可用长度
 *                     |                   |
 *PooledByteBuf------> |                   |-------> 线程缓存---------> PoolThreadCache
 *                     |                   |
 *                     |                   |-------> 其他------>  |------->临时ByteBuffer------tmpNioBuf---------->转换成ByteBuffer对象
 *                     |
 *                     |                                          |------>内存分配器---------->allocator
 *                     |
 *                     |                             |---------->初始化 -----------init
 *                     |                             |
 *                     |                             |                                      |------> getBytes
 *                     |----------------> 方法-------|---------->从Channel中读/写数据-------> |------> setBytes
 *                                                   |
 *                                                   |-------->动态扩容 ----------->capacity
 *                                                   |
 *                                                   |                             |---------> alloc
 *                                                   |--------->分配回收----------->|---------> deallocate
 *                                                                                 |---------> recycle
 *
 *
 *
 * ByteBuf和Allocator 分配器
 * Netty通过ByteBufferAllocator来分配器来创建缓冲区和分配内存空间，Netty提供了ByteBufAllocator的两种实现， PoolByteBufAllocator和
 * UnpooledByteBufAllocator .
 * PoolByteBufAllocator （池化ByteBuf分配器），将ByteBuf实例放入池中， 提高了性能，将内存碎片减少到最小， 这个池化分配器采用了jemalloc高效
 * 内存分配 的策略， 该策略被好几种现在操作系统所采用
 * UnpooledByteBufAllocator是普通的未池化的ByteBuf 分配器，它没有把ByteBuf 放入到池中，每次被调用时，返回一个新的ByteBuf 实例，通过Java 的
 * 垃圾回收机制回收。
 * 为了验证两者的性能，大家可以做一下对比试验 ：
 * 1. 使用UnpooledByteBufAllocator的方式分配ByteBuf 缓冲区， 开启10000个长连接，每秒所有的连接发送一条消息，再看看服务器的内存使用量的情况
 * 实例参考结果： 在短时间之内，可以看到占用10 G 的内存空间， 但随着系统的运行，内存空间不断的增长，直到整个系统内存被占满导致内存溢出 。最终系统
 * 宕机 。
 * 2. 把UnpooledByteBufAllocator 换成PooledByteBufAllocator，再进行试验，看服务器内存的使用量的情况
 * 实验的参考结果，内存的使用量基本维持在一个连接占用1MB左右的内存空间，内存的使用量保存在10GB左右， 经过长时间的运行测试 ， 我们会发现内存的使用量
 * 都能维护在这个数量附近，系统不会因为内存被耗尽而崩溃。
 * 在Netty中，默认的分配器为 BytebufAllocator.DEFAUlT，可以通过Java系统参数System Property的选项io.netty.allocator.type进行配置，
 * 配置时使用字符串值，"unpooled","pooled" ，
 * 不同的Netty 版本，对于分配器的的默认使用策略不一样， 在Netty4.0版本中， 默认的配置为UnpooledByteBufAllocator ， 而在4.1 版本中，默认的分配 器
 * PooledByteBufAllocator 现在PooledByteBufAllocator已经广泛使用了一段时间，并且有了增强的缓冲区泄漏追踪机制，因此，可以在Netty程序中设置启动器
 * Bootstrap的时候，将PooledByteBufAllocator设置为默认的分配器。
 *
 * ServerBootstrap b = new ServerBootstrap () ;
 * 设置通道的参数
 * b.option(ChannelOption.SO_KEEPALIVE,true);
 * b.option(ChannelOption.ALLOCATOR,PooledByteBufAllocator.DEFAULT);
 * b.childOption(ChannelOption.ALLOCATOR.PooledByteBufAllocator.DEFAULT);
 *
 * 内存管理的策略可以灵活的调整，这是使用Netty所带来的又一个好处 ， 只需要简单的配置，就能获得到池化缓冲区所带来的好处，在底层 ， Netty
 * 为我们做了所有的 "脏活，累活" 这主要是因为Netty 用到了Java 的Jemalloc内存管理库。
 * 使用分配器ByteBuf 的方法有多种，下面列出主要的几种
 *
 *
 *
 *
 */
abstract class PooledByteBuf<T> extends AbstractReferenceCountedByteBuf {

    private final Recycler.Handle<PooledByteBuf<T>> recyclerHandle;

    protected PoolChunk<T> chunk;
    protected long handle;
    protected T memory;
    protected int offset;
    protected int length;
    int maxLength;
    PoolThreadCache cache;
    ByteBuffer tmpNioBuf;
    private ByteBufAllocator allocator;

    @SuppressWarnings("unchecked")
    protected PooledByteBuf(Recycler.Handle<? extends PooledByteBuf<T>> recyclerHandle, int maxCapacity) {
        super(maxCapacity);
        this.recyclerHandle = (Handle<PooledByteBuf<T>>) recyclerHandle;
    }

    void init(PoolChunk<T> chunk, ByteBuffer nioBuffer,
              long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        init0(chunk, nioBuffer, handle, offset, length, maxLength, cache);
    }

    void initUnpooled(PoolChunk<T> chunk, int length) {
        init0(chunk, null, 0, chunk.offset, length, length, null);
    }

    private void init0(PoolChunk<T> chunk, ByteBuffer nioBuffer,
                       long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        assert handle >= 0;
        assert chunk != null;
        // 大内存块默认为16MB， 被分配给多个PooledByteBuf
        this.chunk = chunk;
        // chunk中具体的缓存空间
        memory = chunk.memory;
        // 将 PooledByteBuf 转换成ByteBuffer
        tmpNioBuf = nioBuffer;
        // 内存分配器：PooledByteBuf是由 Arena 的分配器构建的
        allocator = chunk.arena.parent;
        // 线程缓存，优先从线程缓存中获取
        this.cache = cache;
        // 通过这个指针可以得到PooledByteBuf 在chunk这棵二叉树中具体位置
        this.handle = handle;
        // 偏移量
        this.offset = offset;
        // 长度 ，实际数据长度
        this.length = length;
        // 写指针不能超过PooledByteBuf的最大可用长度
        this.maxLength = maxLength;
    }

    /**
     * Method must be called before reuse this {@link PooledByteBufAllocator}
     */
    final void reuse(int maxCapacity) {
        maxCapacity(maxCapacity);
        resetRefCnt();
        setIndex0(0, 0);
        discardMarks();
    }

    @Override
    public final int capacity() {
        return length;
    }

    @Override
    public int maxFastWritableBytes() {
        return Math.min(maxLength, maxCapacity()) - writerIndex;
    }

    @Override
    /**
     * 自动扩容
     * newCapacity ： 新的容量值
     */
    public final ByteBuf capacity(int newCapacity) {
        // 若新的容量值与长度相等，则无须扩容，直接返回即可
        if (newCapacity == length) {
            ensureAccessible();
            return this;
        }
        // 检查新的容量值是否大于最大允许容量
        checkNewCapacity(newCapacity);
        /**
         * 非内存池，在新容量值小于最大长度值的情况下，无须重新分配，只需要修改索引和数据长度即可
         */
        if (!chunk.unpooled) {
            // If the request capacity does not require reallocation, just update the length of the memory.
            /**
             * 新的容量值大于长度值
             * 在没有超过Buffer的最大可用长度值时，只需要把长度设为新的容量值即可，若超过了最大可用长度值，则只能重新分配
             */
            if (newCapacity > length) {
                if (newCapacity <= maxLength) {
                    length = newCapacity;
                    return this;
                }
                // 当新的容量值 小于 length
            } else if (newCapacity > maxLength >>> 1 &&
                    (maxLength > 512 || newCapacity > maxLength - 16)) {
                // here newCapacity < length
                // 当新容量值小于最大可用长度值时，其读/写索引不能超过新容量值
                length = newCapacity;
                setIndex(Math.min(readerIndex(), newCapacity), Math.min(writerIndex(), newCapacity));
                return this;
            }
        }

        // Reallocation required.
        // 由Arena重新分配内存并释放旧的内存空间
        chunk.arena.reallocate(this, newCapacity, true);
        return this;
    }

    @Override
    public final ByteBufAllocator alloc() {
        return allocator;
    }

    @Override
    public final ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public final ByteBuf unwrap() {
        return null;
    }

    @Override
    public final ByteBuf retainedDuplicate() {
        return PooledDuplicatedByteBuf.newInstance(this, this, readerIndex(), writerIndex());
    }

    @Override
    public final ByteBuf retainedSlice() {
        final int index = readerIndex();
        return retainedSlice(index, writerIndex() - index);
    }

    @Override
    public final ByteBuf retainedSlice(int index, int length) {
        return PooledSlicedByteBuf.newInstance(this, this, index, length);
    }

    protected final ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = newInternalNioBuffer(memory);
        }
        return tmpNioBuf;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);

    @Override
    /**
     * 对象回收，把对象属性清空
     * 当前面我们提到过的ByteBuf的引用计数为0的时候，会调用deallocate()方法进行释放 ，实现如下 。
     */
    protected final void deallocate() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            memory = null;
            // 释放内存
            chunk.arena.free(chunk, tmpNioBuf, handle, maxLength, cache);
            tmpNioBuf = null;
            chunk = null;
            recycle();
        }
    }

    /**
     *  把PooledByteBuf 放回对象池Stack 中，以便下次使用
     */
    private void recycle() {
        recyclerHandle.recycle(this);
    }

    // 通过offset获取使用内存的初始位置
    protected final int idx(int index) {
        return offset + index;
    }

    final ByteBuffer _internalNioBuffer(int index, int length, boolean duplicate) {
        // 获取读索引
        index = idx(index);
        // 当duplicate为true时，在memory中创建共享此缓冲区内容的新的字节缓冲区
        // 当duplicate为false时，先从tmpNioBuf中获取，当tmpNioBuf 为空时
        // 再调用newInternalNioBuffer，此处与memory的类型有关，因此其具体实现由子类完成
        ByteBuffer buffer = duplicate ? newInternalNioBuffer(memory) : internalNioBuffer();
        // 设置新的缓冲区指针位置及limit
        buffer.limit(index + length).position(index);
        return buffer;
    }

    /**
     * 从 memory中创建了一份缓存ByteBuffer
     * 从memory共享底层数据，但读/写索引独立维护
     */
    ByteBuffer duplicateInternalNioBuffer(int index, int length) {
        // 检查
        checkIndex(index, length);
        return _internalNioBuffer(index, length, true);
    }

    @Override
    public final ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        // 只有当tmpNioBuf为空时才创建新的共享缓冲区
        return _internalNioBuffer(index, length, false);
    }

    @Override
    public final int nioBufferCount() {
        return 1;
    }

    @Override
    public final ByteBuffer nioBuffer(int index, int length) {
        return duplicateInternalNioBuffer(index, length).slice();
    }

    @Override
    public final ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    /**
     *
        *  channel 从PooledByteBuf中获取数据
     *  PooledByteBuf 的读索引变化
     *  由父类AbstractByteBuf 的readBytes()方法维护
     */
    @Override
    public final int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return out.write(duplicateInternalNioBuffer(index, length));
    }

    @Override
    public final int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length, false));
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public final int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return out.write(duplicateInternalNioBuffer(index, length), position);
    }

    @Override
    public final int readBytes(FileChannel out, long position, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length, false), position);
        readerIndex += readBytes;
        return readBytes;
    }

    /**
     *  从channel 中读取数据并写入PooledByteBuf中
     *  writeIndex由父类AbstractByteBuf 的writeBytes()方法维护 ,
     *
     * 通过上述代码解读可知，当内存空间在PoolSubpage中分配成功 后，可以得到一个指针handle。而Netty上层只会运用ByteBuf，那么 ByteBuf是如何跟handle关联的呢?
     * 通过handler可以计算page的偏移量，也可以计算subpage的段在 page中的相对偏移量，两者加起来就是该段分配的内存在chunk中的相 对位置偏移量。当PoolByteBuf从Channel中读取数据时，
     * 需要用这个 相对位置偏移量来获取ByteBuffer。PoolByteBuf从原Buffer池中通过 duplicate()方法在不干扰原来Buffer索引的情况下，与其共享缓冲区 数据，复制一份新的ByteBuffer，
     * 并初始化新ByteBuffer的元数据， 通过offset指定读/写位置，用limit来限制读/写范围。
     */
    @Override
    public final int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        try {
            // 从NioSocketChannel 中读取的请求数据，并将写入PoolByteBuf中
            return in.read(internalNioBuffer(index, length));
        } catch (ClosedChannelException ignored) {
            // 客户端主动关闭连接，返回-1，触发对应的用户事件
            return -1;
        }
    }

    @Override
    public final int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        try {
            return in.read(internalNioBuffer(index, length), position);
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }
}
