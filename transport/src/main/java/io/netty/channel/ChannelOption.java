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

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.AbstractConstant;
import io.netty.util.ConstantPool;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * A {@link ChannelOption} allows to configure a {@link ChannelConfig} in a type-safe
 * way. Which {@link ChannelOption} is supported depends on the actual implementation
 * of {@link ChannelConfig} and may depend on the nature of the transport it belongs
 * to.
 *
 * @param <T>   the type of the value which is valid for the {@link ChannelOption}
 */
public class ChannelOption<T> extends AbstractConstant<ChannelOption<T>> {

    private static final ConstantPool<ChannelOption<Object>> pool = new ConstantPool<ChannelOption<Object>>() {
        @Override
        protected ChannelOption<Object> newConstant(int id, String name) {
            return new ChannelOption<Object>(id, name);
        }
    };

    /**
     * Returns the {@link ChannelOption} of the specified name.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(String name) {
        return (ChannelOption<T>) pool.valueOf(name);
    }

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (ChannelOption<T>) pool.valueOf(firstNameComponent, secondNameComponent);
    }

    /**
     * Returns {@code true} if a {@link ChannelOption} exists for the given {@code name}.
     */
    public static boolean exists(String name) {
        return pool.exists(name);
    }

    /**
     * Creates a new {@link ChannelOption} for the given {@code name} or fail with an
     * {@link IllegalArgumentException} if a {@link ChannelOption} for the given {@code name} exists.
     *
     * @deprecated use {@link #valueOf(String)}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> newInstance(String name) {
        return (ChannelOption<T>) pool.newInstance(name);
    }

    // Netty全局参数， ByteBuf 的分配器，默认值为ByteBufAlloocator.DEFAULT,4.0版本为UnpooledByteBufAllocator。
    // 4.1 版本PooledByteBufAllocator，分别对应的字符串值为"unpooled" 和 "pooled"
    public static final ChannelOption<ByteBufAllocator> ALLOCATOR = valueOf("ALLOCATOR");
    // Netty 全局参数，用于Channel分配接收Buffer的分配器，默认值为AdaptiveRecvByteBufAllocator.DEFAULT，是一个自适应的接收缓冲区分配器。
    // 能根据接收的数据自动调节大小，可选值为FixedRecvByteBufAllocator
    // 固定大小的接收缓冲区分配器
    public static final ChannelOption<RecvByteBufAllocator> RCVBUF_ALLOCATOR = valueOf("RCVBUF_ALLOCATOR");
    // Netty全局参数，消息大小估算器，默认值为DefaultMessageSizeEstimator.DEFAULT。 估算ByteBuf,ByteBuffHolder和
    // 和FileRegion的大小，其中ByteBuf 和ByteBufHolder为实际大小，FileRegion
    // 估算值为0，该值估算的字节数在计算水位时使用，FileRegion为0
    // 可知FileRegion不影响高低水位
    public static final ChannelOption<MessageSizeEstimator> MESSAGE_SIZE_ESTIMATOR = valueOf("MESSAGE_SIZE_ESTIMATOR");
    // Netty全局参数，连接超时毫秒数，默认值为3000ms ，即30s
    public static final ChannelOption<Integer> CONNECT_TIMEOUT_MILLIS = valueOf("CONNECT_TIMEOUT_MILLIS");
    /**
     * @deprecated Use {@link MaxMessagesRecvByteBufAllocator}
     * and {@link MaxMessagesRecvByteBufAllocator#maxMessagesPerRead(int)}.
     */
    @Deprecated
    public static final ChannelOption<Integer> MAX_MESSAGES_PER_READ = valueOf("MAX_MESSAGES_PER_READ");
    public static final ChannelOption<Integer> WRITE_SPIN_COUNT = valueOf("WRITE_SPIN_COUNT");



    /**
     * @deprecated Use {@link #WRITE_BUFFER_WATER_MARK}
     */
    @Deprecated
    public static final ChannelOption<Integer> WRITE_BUFFER_HIGH_WATER_MARK = valueOf("WRITE_BUFFER_HIGH_WATER_MARK");
    /**
     * @deprecated Use {@link #WRITE_BUFFER_WATER_MARK}
     */
    @Deprecated
    public static final ChannelOption<Integer> WRITE_BUFFER_LOW_WATER_MARK = valueOf("WRITE_BUFFER_LOW_WATER_MARK");
    // Netty全局参数，设置某个连接上可以暂存的最大最小Buffer,若连接等待发送的数据量大于设置的值，则isWritable()会返回不可写
    // 这样，客户端可以不再发送，防止这个量不断的积压 ， 最终可能让客户端挂掉
    public static final ChannelOption<WriteBufferWaterMark> WRITE_BUFFER_WATER_MARK = valueOf("WRITE_BUFFER_WATER_MARK");

    // Netty全局参数，一个连接远端关闭时本地端是否关闭，默认值为false，值为false时，连接自动关闭
    public static final ChannelOption<Boolean> ALLOW_HALF_CLOSURE = valueOf("ALLOW_HALF_CLOSURE");
    public static final ChannelOption<Boolean> AUTO_READ = valueOf("AUTO_READ");

    /**
     * If {@code true} then the {@link Channel} is closed automatically and immediately on write failure.
     * The default value is {@code true}.
     */
    // Netty 全局参数，自动读取，默认值为true, Netty 只有在必要 的时候才设置关心相应的IO事件，对于读操作，需要
    // 调用channel.read()设置关心的I/O事件为OP_READ, 这样若有数据到达时才能读取以供用户处理
    public static final ChannelOption<Boolean> AUTO_CLOSE = valueOf("AUTO_CLOSE");

    public static final ChannelOption<Boolean> SO_BROADCAST = valueOf("SO_BROADCAST");
    public static final ChannelOption<Boolean> SO_KEEPALIVE = valueOf("SO_KEEPALIVE");
    // Socket 参数，用于设置接收数据的等待超时时间，单位为ms,默认值为0 ，表示无限等待
    public static final ChannelOption<Integer> SO_SNDBUF = valueOf("SO_SNDBUF");
    // Socket参数，TCP 数据接收缓冲区的大小，缓冲区即TCP 接收滑动窗口，Linux 操作系统可以使用命令
    // cat /proc/sys/net/ipv4/tcp_rmem 查询大小，一般情况下， 该值可由用户 任意时刻设置，但当设置值超过64KB
    // 时，需要在连接到远端之前设置
    public static final ChannelOption<Integer> SO_RCVBUF = valueOf("SO_RCVBUF");
    // Socket 参数，地址复用，默认值为false,有4种情况可以使用，1，当有一个有相同的本地地址和端口的Socket1处于TIME_WAIT状态时
    // 你希望启动的程序Socket2 要占用该地址和端口，比如重启服务且保持先前的端口，有多块网卡或用IP Alias技术的机器在同一端启动多个
    // 进程,但每个进程 绑定的本地IP地址可能不同， 单个进程绑定的相同的端口有多个Socket 上，但每个Socket绑定的IP地址可能不同 。
    // 4 完全相同的越来越和端口重新绑定，但这里只用于UDP的多皤，不用于TCP.
    public static final ChannelOption<Boolean> SO_REUSEADDR = valueOf("SO_REUSEADDR");
    // Socket参数，关闭Socket的延迟时间，默认值为-1， 表示禁用该功能，-1 表示socket.close()方法立即返回。但操作系统底层会将发送缓冲区
    // 的数据全部 发送到对端，0表示socket.close()方法立即返回，操作系统放弃发送缓冲区的数据直接向对端发送RST包， 对端收到复位
    // 错误，非0整数值表示调用socket.close()方法的线程被阻塞直到延迟时间到或缓冲区的数据发送完毕，若超时，则对端会收到复位错误。
    public static final ChannelOption<Integer> SO_LINGER = valueOf("SO_LINGER");
    public static final ChannelOption<Integer> SO_BACKLOG = valueOf("SO_BACKLOG");
    public static final ChannelOption<Integer> SO_TIMEOUT = valueOf("SO_TIMEOUT");

    public static final ChannelOption<Integer> IP_TOS = valueOf("IP_TOS");
    // IP 参数 ， 对应的IP参数IP_MULTICAST_IF ， 设置对应的地址为网卡多播模式
    public static final ChannelOption<InetAddress> IP_MULTICAST_ADDR = valueOf("IP_MULTICAST_ADDR");
    // IP 参数，对应的IP参数IP_MULTICAST2 , 同上，支持IP6
    public static final ChannelOption<NetworkInterface> IP_MULTICAST_IF = valueOf("IP_MULTICAST_IF");
    // IP 参数， 多播数据报Time-to-Live，即存活跳数
    public static final ChannelOption<Integer> IP_MULTICAST_TTL = valueOf("IP_MULTICAST_TTL");
    // IP 参数，对应的IP参数IP_MULTICAST_LOOP，设置本地回环接口的多播功能，
    // 由于IP_MULTICAST_LOOP返回true，表示关闭，所以，Netty 加后缀_DISABLED防止歧义
    public static final ChannelOption<Boolean> IP_MULTICAST_LOOP_DISABLED = valueOf("IP_MULTICAST_LOOP_DISABLED");

    // TCP 参数，表示立即发送数据，默认值为true （Netty 默认值为true 而操作系统默认值为false）该值设置Nagle算法的启动
    public static final ChannelOption<Boolean> TCP_NODELAY = valueOf("TCP_NODELAY");

    @Deprecated
    public static final ChannelOption<Boolean> DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION =
            valueOf("DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION");

    // 单线程执行ChannelPipeline中的事件，默认值为true,该值控制执行ChannelPipeline中执行ChannelHandler的线程。
    // 如果为true, 整个pipeline由一个线程执行，这样不需要进行线程切换以及线程同步，是Netty 4 的推荐做法，如为false
    // channelHandler 中处理过程会由Group中不同的线程执行
    public static final ChannelOption<Boolean> SINGLE_EVENTEXECUTOR_PER_GROUP =
            valueOf("SINGLE_EVENTEXECUTOR_PER_GROUP");

    /**
     * Creates a new {@link ChannelOption} with the specified unique {@code name}.
     */
    private ChannelOption(int id, String name) {
        super(id, name);
    }

    @Deprecated
    protected ChannelOption(String name) {
        this(pool.nextId(), name);
    }

    /**
     * Validate the value which is set for the {@link ChannelOption}. Sub-classes
     * may override this for special checks.
     */
    public void validate(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
    }
}
