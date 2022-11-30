/*
 * Copyright 2019 The Netty Project
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
package io.netty.util.internal;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCounted;

/**
 * Common logic for {@link ReferenceCounted} implementations
 * ReferenceCountUpdater 是 AbstractReferenceCountedByteBuf 的 辅助类，用于完成对引用计数值的具体操作，其功能如图4-9所示。
 * 虽 然它的所有功能基本上都与引用计数有关，但与Netty之前的版本相比 有很大的改动，主要是Netty v4.1.38.Final版本采用了乐观锁方式来
 * 修改refCnt，并在修改后进行校验。例如，retain()方法在增加了refCnt后，如果出现了溢出，则回滚并抛异常。在旧版本中，采用的 是原子性操作，
 * 不断地提前判断，并尝试调用compareAndSet。与之相 比，新版本的吞吐量有所提高，但若还是采用refCnt的原有方式，
 * 从1 开始每次加1或减1，则会引发一些问题，需要重新设计。这也是新版 本改动较大的主要原因。
 *
 * 图4-9 ReferenceCountedUpdater功能图
 *
 *                                  | --------> 获取字段偏移量---------> unsafeOffset
 *                                  | --------> 获取原子整形字段更新操作类---------> updater
 *                                  |
 *                                  |
 *                                  |
 *                                  |                                    |--------> 初始化-------> initialValue
 *                                  |                                    |                                                      |---------->realRefCnt
 *                                  |                                    |--------> 通过未处理的引用值获取处理后真实引用次数-------> |----------> toLiveRealRefCnt
 *                                  |                                    |
 * ReferenceCountUpdater----------> | --------> refCnt操作方法 ---------> |--------> 继续引用retain------------>retain0
 *                                  |                                    |                                |-----> 非volatile操作---------> noVolatileRawCnt
 *                                  |                                    |--------> 释放release---------->|------> 最后一次释放 ----------> tryFinalRelease0
 *                                  |                                                                     |
 *                                  |                                                                     |                     |--------> nonFinalRelease0
 *                                  |                                                                     |------> 非最后一次释放 |-------> retryRelease0
 *                                  |
 *
 *
 *
 *
 * ReferenceCountUpdater主要运用JDK的CAS来修改计数器，为了提 高性能，还引入了Unsafe类，可直接操作内存。至此，ByteBuf的引用
 * 计数告一段落，下面会对Netty的另一种零拷贝方式(组合缓冲区视图 CompositeByteBuf)进行详细剖析。
 *
 *
 */
public abstract class ReferenceCountUpdater<T extends ReferenceCounted> {
    /*
     * Implementation notes:
     *
     * For the updated int field:
     *   Even => "real" refcount is (refCnt >>> 1)
     *   Odd  => "real" refcount is 0
     *
     * (x & y) appears to be surprisingly expensive relative to (x == y). Thus this class uses
     * a fast-path in some places for most common low values when checking for live (even) refcounts,
     * for example: if (rawCnt == 2 || rawCnt == 4 || (rawCnt & 1) == 0) { ...
     */

    protected ReferenceCountUpdater() {
    }

    public static long getUnsafeOffset(Class<? extends ReferenceCounted> clz, String fieldName) {
        try {
            if (PlatformDependent.hasUnsafe()) {
                return PlatformDependent.objectFieldOffset(clz.getDeclaredField(fieldName));
            }
        } catch (Throwable ignore) {
            // fall-back
        }
        return -1;
    }

    protected abstract AtomicIntegerFieldUpdater<T> updater();

    protected abstract long unsafeOffset();

    public final int initialValue() {
        return 2;
    }

    private static int realRefCnt(int rawCnt) {
        return rawCnt != 2 && rawCnt != 4 && (rawCnt & 1) != 0 ? 0 : rawCnt >>> 1;
    }

    /**
     * Like {@link #realRefCnt(int)} but throws if refCnt == 0
     *
     */
    private static int toLiveRealRefCnt(int rawCnt, int decrement) {
        // 当rawCnt的值为偶数时，真实引用的值需要右移1位
        if (rawCnt == 2 || rawCnt == 4 || (rawCnt & 1) == 0) {
            return rawCnt >>> 1;
        }
        // odd rawCnt => already deallocated
        // rawCnt为奇数表示已经释放，此时会抛出异常
        throw new IllegalReferenceCountException(0, -decrement);
    }

    private int nonVolatileRawCnt(T instance) {
        // TODO: Once we compile against later versions of Java we can replace the Unsafe usage here by varhandles.
        // 获取偏移量
        final long offset = unsafeOffset();
        /**
         * 若偏移量正常，则选择Unsafe的普通get
         * 若偏移量获取异常，则选择Unsafe的volatile get
         */
        return offset != -1 ? PlatformDependent.getInt(instance, offset) : updater().get(instance);
    }

    public final int refCnt(T instance) {
        return realRefCnt(updater().get(instance));
    }

    public final boolean isLiveNonVolatile(T instance) {
        final long offset = unsafeOffset();
        final int rawCnt = offset != -1 ? PlatformDependent.getInt(instance, offset) : updater().get(instance);

        // The "real" ref count is > 0 if the rawCnt is even.
        return rawCnt == 2 || rawCnt == 4 || rawCnt == 6 || rawCnt == 8 || (rawCnt & 1) == 0;
    }

    /**
     * An unsafe operation that sets the reference count directly
     */
    public final void setRefCnt(T instance, int refCnt) {
        updater().set(instance, refCnt > 0 ? refCnt << 1 : 1); // overflow OK here
    }

    /**
     * Resets the reference count to 1
     */
    public final void resetRefCnt(T instance) {
        updater().set(instance, initialValue());
    }

    public final T retain(T instance) {
        return retain0(instance, 1, 2);
    }

    public final T retain(T instance, int increment) {
        // all changes to the raw count are 2x the "real" change - overflow is OK
        int rawIncrement = checkPositive(increment, "increment") << 1;
        return retain0(instance, increment, rawIncrement);
    }

    // rawIncrement == increment << 1
    // 由duplicate()、slice()衍生的ByteBuf与原对象共享底层的 Buffer，原对象的引用可能需要增加，引用增加的方法为retain0()。
    // retain0()方法为retain()方法的具体实现，其代码解读如下:
    private T retain0(T instance, final int increment, final int rawIncrement) {
        // 乐观锁，先获取原值，再增加
        int oldRef = updater().getAndAdd(instance, rawIncrement);
        // 如果原值不为偶数，则表示ByteBuf 已经被释放了，无法继续引用
        if (oldRef != 2 && oldRef != 4 && (oldRef & 1) != 0) {
            throw new IllegalReferenceCountException(0, increment);
        }
        // don't pass 0!
        // 如果增加后出现溢出
        if ((oldRef <= 0 && oldRef + rawIncrement >= 0)
                || (oldRef >= 0 && oldRef + rawIncrement < oldRef)) {
            // overflow case
            // 则回滚并抛出异常
            updater().getAndAdd(instance, -rawIncrement);
            throw new IllegalReferenceCountException(realRefCnt(oldRef), increment);
        }
        return instance;
    }

    // retain0()方法的旧版本如下 ：
    // private ByteBuf remain0(int increment){
    //      // 一直循环
    //      for(;;){
    //          int refCnt = this.refCnt;
    //          final int nextCnt = refCnt + increment;
    //          // 先判断是否溢出
    //          if(nextCnt <= increment){
    //              throw new IllegalReferenceCountException(refCnt, increment);
    //          }
    //          // 如果引用在for 循环体中未被修改过， 则用新的引用值替换
    //          if(refCntUpdter.compareAndSet(this,refCnt ,nextCnt)){
    //              break;
    //          }
    //      }
    //     return this ;
    // }
    // 在进行引用计数的修改时，并不会先判断是否会出现溢出，而是 先执行，执行完之后再进行判断，如果溢出则进行回滚。在高并发情 况下，与之前的版本相比，
    // Netty v4.1.38.Final的吞吐量会有所提 升，但refCnt不是每次都进行加1或减1的操作，主要原因是修改前无 判断。若有多条线程同时操作，
    // 则线程1调用ByteBuf的release()方 法，线程2调用retain()方法，线程3调用release()方法。
    // 线程1执行完后，refCnt的值为0;线程2执行完retain()方法后， 正好执行完增加操作，refCnt此时由0变成1，还未执行到判断回滚环 节;
    // 此时线程3执行release()方法，能正常运行，导致ByteBuf出现多次销毁操作。若采用奇数表示销毁状态，偶数表示正常状态，则该问 题得以解决，最终释放后会变成奇数。
    // ByteBuf使用完后需要执行release()方法。release()方法的返回 值为true或false，false表示还有引用存在;true表示无引用，此时 会调用ByteBuf的deallocate()方法进行销毁。相关代码解读如下:

    public final boolean release(T instance) {
        /**
         * 先采用普通方法获取refCnt的值，无须采用volatile获取
         * 因为tryFinalRelease0()方法会用CAS更新
         * 若更新失败了，则通过retryRelease0()方法进行不断的循环处理
         * 此处一开始并非调用retryRelease0()方法循环尝试来修改refCnt的值
         * 这样设计，吞吐量会有所提升
         * 当rawCnt不等于2时，说明还有其他地方引用了此对象
         * 调用nonFinalRelease0()方法，尝试采用CAS使refCnt的值减2
         */
        int rawCnt = nonVolatileRawCnt(instance);
        return rawCnt == 2 ? tryFinalRelease0(instance, 2) || retryRelease0(instance, 1)
                : nonFinalRelease0(instance, 1, rawCnt, toLiveRealRefCnt(rawCnt, 1));
    }

    public final boolean release(T instance, int decrement) {
        int rawCnt = nonVolatileRawCnt(instance);
        int realCnt = toLiveRealRefCnt(rawCnt, checkPositive(decrement, "decrement"));
        return decrement == realCnt ? tryFinalRelease0(instance, rawCnt) || retryRelease0(instance, decrement)
                : nonFinalRelease0(instance, decrement, rawCnt, realCnt);
    }

    // 采用CAS最终释放，将refCnt的设置为1
    private boolean tryFinalRelease0(T instance, int expectRawCnt) {
        return updater().compareAndSet(instance, expectRawCnt, 1); // any odd number will work
    }

    /**
     * 非最后一次释放，realCnt > 1
     * @param instance
     * @param decrement
     * @param rawCnt
     * @param realCnt
     * @return
     */
    private boolean nonFinalRelease0(T instance, int decrement, int rawCnt, int realCnt) {
        // 与retryRelease0()方法中的其中一个释放分支一样
        if (decrement < realCnt
                // all changes to the raw count are 2x the "real" change - overflow is OK
                && updater().compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
            return false;
        }
        //  若CAS更新失败，则进入retryRelease0
        return retryRelease0(instance, decrement);
    }

    private boolean retryRelease0(T instance, int decrement) {
        for (;;) {
            /***
             * volatile获取refCnt的原始值
             * 并通过toLiveRealRefCnt()方法将其转化成真正的引用次数
             * 原始值必须是2的倍数，否则状态为已释放，会抛出异常
             */
            int rawCnt = updater().get(instance), realCnt = toLiveRealRefCnt(rawCnt, decrement);
            // 如果引用次数与当前释放次数相等
            if (decrement == realCnt) {
                /**
                 * 尝试最终释放，采用CAS更新refCnt的值为1，若更新成功则返回true
                 * 如果更新失败，说明refCnt的值改变了， 则继续进行循环处理
                 */
                if (tryFinalRelease0(instance, rawCnt)) {
                    return true;
                }
            } else if (decrement < realCnt) {
                // all changes to the raw count are 2x the "real" change
                /**
                 * 引用次数大于当前释放的次数
                 * CAS更新refCnt的值
                 * 引用原始值- 2 * 当前释放的次数
                 * 此处释放为非最后一次释放
                 * 因此释放成功后会以返回false
                 */
                if (updater().compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
                    return false;
                }
            } else {
                throw new IllegalReferenceCountException(realCnt, -decrement);
            }
            Thread.yield(); // this benefits throughput under high contention
        }
    }
}
