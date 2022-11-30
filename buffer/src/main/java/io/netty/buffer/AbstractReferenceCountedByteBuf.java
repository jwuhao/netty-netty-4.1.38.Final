/*
 * Copyright 2013 The Netty Project
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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.internal.ReferenceCountUpdater;

/**
 * Abstract base class for {@link ByteBuf} implementations that count references.
 * Netty在进行I/O的读/写时使用了堆外直接内存，实现了零拷贝， 堆外直接内存Direct Buffer的分配与回收效率要远远低于JVM堆内存 上对象的创建
 * 与回收速率。Netty使用引用计数法来管理Buffer的引用 与释放。Netty采用了内存池设计，先分配一块大内存，然后不断地重 复利用这块内存。
 * 例如，当从SocketChannel中读取数据时，先在大内 存块中切一小部分来使用，由于与大内存共享缓存区，所以需要增加 大内存的引用值，
 * 当用完小内存后，再将其放回大内存块中，同时减 少其引用值。
 *                                                                                                       |--------> AtomicIntegerFieldUpdater
 *                                                         |-------> ReferenceCountUpdaterUpdater ------>|--------> unsafeOffset
 *                                       |------->  属性--->| valatile int refCnt
 *                                       |                                          |----------> refCnt
 *                                       |                                          |----------> setRefCnf
 *                                       |------>  引用计数值，获取，设置，重置-------> |--------->  resetRefCnt
 *                                       |
 *                                       |
 * AbstractReferenceCountedByteBuf------>|                            |-----------> retain
 *                                       |-------> 计数值增大 -------> |------------> retain(int increment)
 *                                       |
 *                                       |
 *                                       |
 *                                       |-------> 计数值减少 |------------> release
 *                                       |                   |------------> release(decrement)
 *
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {
    /**
     * 调用Unsafe类的objectFieldOffset()方法
     * 以获取某个字段相对于Java对象的起始地址的偏移量
     * Netty为了提升性能，构建Unsafe 对象
     * 采用此偏移量访问ByteBuf 的refCnt字段
     * 并未直接使用AtomicIntegerFieldUpdater来操作
     *
     */
    private static final long REFCNT_FIELD_OFFSET =
            ReferenceCountUpdater.getUnsafeOffset(AbstractReferenceCountedByteBuf.class, "refCnt");

    /**
     *  AtomicIntegerFieldUpdater 属性委托给ReferenceCountUpdater来管理
     *  主要用于更新和获取efCnt的值
     */
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> AIF_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");


    // 引用计数值的实际管理者
    private static final ReferenceCountUpdater<AbstractReferenceCountedByteBuf> updater =
            new ReferenceCountUpdater<AbstractReferenceCountedByteBuf>() {
        @Override
        protected AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> updater() {
            return AIF_UPDATER;
        }
        @Override
        protected long unsafeOffset() {
            return REFCNT_FIELD_OFFSET;
        }
    };

    // Value might not equal "real" reference count, all access should be via the updater
    //运 用 到 引 用 计 数 法 的 ByteBuf 大 部 分 都 需 要 继 承 AbstractReferenceCountedByteBuf 类 。
    // 该 类 有 个 引 用 值 属 性 —— refCnt ， 其 功 能 大 部 分 与 此 属 性 有 关 。 图 4-8 为 AbstractReferenceCountedByteBuf的功能图。
    //
    //由于ByteBuf的操作可能存在多线程并发使用的情况，其refCnt属 性的操作必须是线程安全的，因此采用了volatile来修饰，以保证其 多线程可见。
    // 在Netty中，ByteBuf会被大量地创建，为了节省内存开销，通过AtomicIntegerFieldUpdater来更新refCnt的值，而没有采用 AtomicInteger类型。
    // 因为AtomicInteger类型创建的对象比int类型多 占用16B的对象头，当有几十万或几百万ByteBuf对象时，节约的内存 可能就是几十MB或几百MB。

    // 引用计数值，初始化为2，与调用refCnt()获取的实际值1有差别
    //  在旧的版本中，refCnt引用计数的值每次加1或减1，默认为1，大 于0表示可用，等于0表示已释放。在Netty v4.1.38.Final版本中，
    //  refCnt的初始值为2，每次操作也不同。那么，为何要改成这种设计方 式?有什么好处呢?通过4.4.3小节来解决这个疑问。
    @SuppressWarnings("unused")
    private volatile int refCnt = updater.initialValue();

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    boolean isAccessible() {
        // Try to do non-volatile read for performance as the ensureAccessible() is racy anyway and only provide
        // a best-effort guard.
        return updater.isLiveNonVolatile(this);
    }

    @Override
    public int refCnt() {
        return updater.refCnt(this);
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int refCnt) {
        updater.setRefCnt(this, refCnt);
    }

    /**
     * An unsafe operation intended for use by a subclass that resets the reference count of the buffer to 1
     */
    protected final void resetRefCnt() {
        updater.resetRefCnt(this);
    }

    @Override
    public ByteBuf retain() {
        return updater.retain(this);
    }

    @Override
    public ByteBuf retain(int increment) {
        return updater.retain(this, increment);
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return handleRelease(updater.release(this));
    }

    @Override
    public boolean release(int decrement) {
        return handleRelease(updater.release(this, decrement));
    }

    private boolean handleRelease(boolean result) {
        if (result) {
            deallocate();
        }
        return result;
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}
