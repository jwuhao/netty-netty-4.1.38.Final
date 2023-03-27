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
package io.netty.channel;

import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * The {@link RecvByteBufAllocator} that automatically increases and
 * decreases the predicted buffer size on feed back.
 * <p>
 * It gradually increases the expected number of readable bytes if the previous
 * read fully filled the allocated buffer.  It gradually decreases the expected
 * number of readable bytes if the read operation was not able to fill a certain
 * amount of the allocated buffer two times consecutively.  Otherwise, it keeps
 * returning the same prediction.
 * <p>
 * AdaptiveRecvByteBufAllocator 内 部 维 护 了 一 个 SIZE_TABLE 数 组，记录了不同的内存块大小，按照分配需要寻找最合适的内存块。
 * SIZE_TABLE数组中的值都是2n ，这样便于软硬件进行处理， SIZE_TABLE数组的初始化与PoolArena中的normalizeCapacity的初识 化类似。
 * 当需要的内存很小时，增长的幅度不大;当需要的内存较大 时，增长幅度比较大。因此在[16,512]区间每次增加16，直到512;而 从512起，每次翻一倍，直到int的最大值。
 * <p>
 * <p>
 * 当对内部计算器Handle的具体实现类HandleImpl进行初始化时， 可根据AdaptiveRecvByte BufAllocator的getSizeTableIndex二分查 找方法获
 * 取SIZE_TABLE的下标index并保存，通过SIZE_TABLE [index] 获取下次需要分配的缓冲区的大小nextReceiveBufferSize并记录。缓 冲区的最小
 * 容量属性对应SIZE_TABLE中的下标为minIndex的值，最大 容量属性对应SIZE_TABLE中的下标为maxIndex的值及bool类型标识属 性decreaseNow。
 * 这3个属性用于判断下一次创建的缓冲区是否需要减 小。
 * <p>
 * <p>
 * NioByteUnsafe每次读循环完成后会根据实际读取到的字节数和当 前缓冲区的大小重新设置下次需要分配的缓冲区的大小，具体代码解 读如下:
 */
public class AdaptiveRecvByteBufAllocator extends DefaultMaxMessagesRecvByteBufAllocator {

    static final int DEFAULT_MINIMUM = 64;                      // 接收缓冲区的最小长度下限
    static final int DEFAULT_INITIAL = 1024;                    // 接收缓冲区的最大长度上限
    static final int DEFAULT_MAXIMUM = 65536;                   // 接收缓冲区最大长度上限


    // 在调整缓冲区大小时，若是增加缓冲区容量，那么增加的索引值。
    // 比如，当前缓冲区的大小为SIZE_TABLE[20],若预测下次需要创建的缓冲区需要增加容量大小，
    // 则新缓冲区的大小为SIZE_TABLE[20 + INDEX_INCREMENT]，即SIZE_TABLE[24]
    private static final int INDEX_INCREMENT = 4;               // 扩容增长量
    // 在调整缓冲区大小时，若是减少缓冲区容量，那么减少的索引值。
    // 比如，当前缓冲区的大小为SIZE_TABLE[20],若预测下次需要创建的缓冲区需要减小容量大小，
    // 则新缓冲区的大小为SIZE_TABLE[20 - INDEX_DECREMENT]，即SIZE_TABLE[19]
    private static final int INDEX_DECREMENT = 1;               // 扩容减少量

    private static final int[] SIZE_TABLE;

    // 分配了一个int类型的数组，并进行了数组的初始化处理， 从实现来看，该数组的长度是53，前32位是16的倍数，value值是从16开始的，到512，从33位开始，值是前一位的
    // 两倍，即从1024，2048 ， 到最大值 1073741824 。
    static {

        List<Integer> sizeTable = new ArrayList<Integer>();
        for (int i = 16; i < 512; i += 16) {
            sizeTable.add(i);
        }

        for (int i = 512; i > 0; i <<= 1) {
            sizeTable.add(i);
        }

        SIZE_TABLE = new int[sizeTable.size()];
        for (int i = 0; i < SIZE_TABLE.length; i++) {
            SIZE_TABLE[i] = sizeTable.get(i);
        }
        System.out.println("================");
    }

    /**
     * @deprecated There is state for {@link #maxMessagesPerRead()} which is typically based upon channel type.
     */
    @Deprecated
    public static final AdaptiveRecvByteBufAllocator DEFAULT = new AdaptiveRecvByteBufAllocator();

    // 入参是一个大小，然后利用二分查找法对该数组进行size定位 ，目标是为了找出该size值在数组中的下标位置 ， 主要是为了初始化maxIndex, maxIndex这两个参数
    private static int getSizeTableIndex(final int size) {
        for (int low = 0, high = SIZE_TABLE.length - 1; ; ) {
            if (high < low) {
                return low;
            }
            if (high == low) {
                return high;
            }

            int mid = low + high >>> 1;
            int a = SIZE_TABLE[mid];
            int b = SIZE_TABLE[mid + 1];
            if (size > b) {
                low = mid + 1;
            } else if (size < a) {
                high = mid - 1;
            } else if (size == a) {
                return mid;
            } else {
                return mid + 1;
            }
        }
    }

    private final class HandleImpl extends MaxMessageHandle {
        private final int minIndex; // 缓冲区最小容量对应于SIZE_TABLE中的下标位置，同外部类AdaptiveRecvByteBufAllocator是一个值
        private final int maxIndex; // 缓冲区最大容量对应于SIZE_TABLE中的下标位置，同外部类AdaptiveRecvByteBufAllocator是一个值
        private int index;          // 缓冲区默认容量对应于SIZE_TABLE中的下标位置，外部类AdaptiveRecvByteBufAllocator记录的是容量大小值，而HandleImpl中记录是其值对应于SIZE_TABLE中的下标位置
        private int nextReceiveBufferSize;      // 下一次创建缓冲区时的其容量的大小。
        private boolean decreaseNow;            // 在record()方法中使用，用于标识是否需要减少下一次创建的缓冲区的大小。

        HandleImpl(int minIndex, int maxIndex, int initial) {
            this.minIndex = minIndex;
            this.maxIndex = maxIndex;
            // 用initial获取一开始初始化缓冲的下标，在根据SIZE_TABLE查找分配bufferSize
            index = getSizeTableIndex(initial);
            nextReceiveBufferSize = SIZE_TABLE[index];
        }

        @Override
        public void lastBytesRead(int bytes) {
            // If we read as much as we asked for we should check if we need to ramp up the size of our next guess.
            // This helps adjust more quickly when large amounts of data is pending and can avoid going back to
            // the selector to check for more data. Going back to the selector can add significant latency for large
            // data transfers.
            if (bytes == attemptedBytesRead()) {
                record(bytes);
            }
            super.lastBytesRead(bytes);
        }

        @Override
        public int guess() {
            return nextReceiveBufferSize;
        }

        // 该方法的参数是一次读取操作中实际读取到的数据大小，将其与nextReceiveBufferSize 进行比较，如果实际字节数actualReadBytes大于等于该值，则立即更新nextReceiveBufferSize ，
        // 其更新后的值与INDEX_INCREMENT有关。INDEX_INCREMENT为默认常量，值为4。也就是说在扩容时会一次性增大多一些，以保证下次有足够空间可以接收数据。而相对扩容的策略，
        // 缩容策略则实际保守些，常量为INDEX_INCREMENT，值为1，同样也是进行对比， 但不同的是，若实际字节小于所用nextReceiveBufferSize，并不会立马进行大小调整，
        // 而是先把 decreaseNow 设置为true，如果下次仍然小于，则才会减少nextReceiveBufferSize的大小
        private void record(int actualReadBytes) {
            if (actualReadBytes <= SIZE_TABLE[max(0, index - INDEX_DECREMENT - 1)]) {
                if (decreaseNow) {                      // 若减少标识decreaseNow连续两次为true, 则说明下次读取字节数需要减少SIZE_TABLE下标减1
                    index = max(index - INDEX_DECREMENT, minIndex);
                    nextReceiveBufferSize = SIZE_TABLE[index];
                    decreaseNow = false;
                } else {
                    decreaseNow = true;                     // 第一次减少，只做记录
                }
            } else if (actualReadBytes >= nextReceiveBufferSize) {                // 实际读取的字节大小要大于或等于预测值
                index = min(index + INDEX_INCREMENT, maxIndex);             // SIZE_TABLE 下标 + 4
                nextReceiveBufferSize = SIZE_TABLE[index];      // 若当前缓存为512，则变成 512 * 2 ^ 4
                decreaseNow = false;
            }
        }

        @Override
        // 循环读取完后被调用
        public void readComplete() {
            record(totalBytesRead());
        }
    }

    private final int minIndex;
    private final int maxIndex;
    private final int initial;

    /**
     * Creates a new predictor with the default parameters.  With the default
     * parameters, the expected buffer size starts from {@code 1024}, does not
     * go down below {@code 64}, and does not go up above {@code 65536}.
     */
    public AdaptiveRecvByteBufAllocator() {
        this(DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM);
    }

    /**
     * Creates a new predictor with the specified parameters.
     *
     * @param minimum the inclusive lower bound of the expected buffer size
     * @param initial the initial buffer size when no feed back was received
     * @param maximum the inclusive upper bound of the expected buffer size
     */
    public AdaptiveRecvByteBufAllocator(int minimum, int initial, int maximum) {
        checkPositive(minimum, "minimum");
        if (initial < minimum) {
            throw new IllegalArgumentException("initial: " + initial);
        }
        if (maximum < initial) {
            throw new IllegalArgumentException("maximum: " + maximum);
        }

        int minIndex = getSizeTableIndex(minimum);
        if (SIZE_TABLE[minIndex] < minimum) {
            this.minIndex = minIndex + 1;
        } else {
            this.minIndex = minIndex;
        }

        int maxIndex = getSizeTableIndex(maximum);
        if (SIZE_TABLE[maxIndex] > maximum) {
            this.maxIndex = maxIndex - 1;
        } else {
            this.maxIndex = maxIndex;
        }

        this.initial = initial;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Handle newHandle() {
        return new HandleImpl(minIndex, maxIndex, initial);
    }

    @Override
    public AdaptiveRecvByteBufAllocator respectMaybeMoreData(boolean respectMaybeMoreData) {
        super.respectMaybeMoreData(respectMaybeMoreData);
        return this;
    }


    public static void main(String[] args) throws Exception {
        AdaptiveRecvByteBufAllocator allocator = new AdaptiveRecvByteBufAllocator();
        RecvByteBufAllocator.Handle handle = allocator.newHandle();
        System.out.println("==============开始 I/O 读事件模拟==============");
        // 读取循环开始前先重置，将读取的次数和字节数设置为0， 将totalMessages与totalBytesRead设置为0
        handle.reset(null);
        System.out.println(String.format("第一次模拟读，需要分配大小 ：%d", handle.guess()));
        handle.lastBytesRead(256);
        // 调整下次预测值
        handle.readComplete();
        // 在每次读取数据时都需要重置totalMessage 与totalBytesRead
        handle.reset(null);
        System.out.println(String.format("第2次花枝招展读，需要分配大小：%d ", handle.guess()));
        handle.lastBytesRead(256);
        handle.readComplete();

        System.out.println("===============连续2次读取的字节数小于默认分配的字节数= =========================");
        handle.reset(null);
        System.out.println(String.format("第3次模拟读，需要分配大小 ： %d", handle.guess()));
        handle.lastBytesRead(512);
        // 调整下次预测值，预测值应该增加到512 * 2 ^ 4
        handle.readComplete();

        System.out.println("==================读取的字节数变大 ===============");
        handle.reset(null);
        // 读循环中缓冲区的大小
        System.out.println(String.format("第4次模拟读，需要分配的大小为:%d ", handle.guess()));


    }
}
