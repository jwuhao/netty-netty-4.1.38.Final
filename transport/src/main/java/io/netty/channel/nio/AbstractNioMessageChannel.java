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
package io.netty.channel.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.ServerChannel;

import java.io.IOException;
import java.net.PortUnreachableException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on messages.
 * AbstractNioMessageChannel写入和读取的数据类型是Object，而 不是字节流，那么它的读/写方法与AbstractNioByteChannel的读/写
 * 方法有哪些不同呢?下面进行详细讲解。
 * 在读数据时，AbstractNioMessageChannel数据不存在粘包问题， 因此AbstractNioMessageChannel在read()方法中先循环读取数据包， 再触发channelRead事件。
 *
 * 在写数据时，AbstractNioMessageChannel数据逻辑简单。它把缓 存outboundBuffer中的数据包依次写入Channel中。如果Channel写满 了，
 * 或循环写、默认写的次数为子类Channel属性METADATA中的 defaultMaxMessagesPerRead次数，则在Channel的SelectionKey上设 置OP_WRITE事件，
 * 随后退出，其后OP_WRITE事件处理逻辑和Byte字节 流写逻辑一样。read()与doWrite()方法的代码解读如下:
 */
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {
    boolean inputShutdown;

    /**
     * @see AbstractNioChannel#AbstractNioChannel(Channel, SelectableChannel, int)
     */
    protected AbstractNioMessageChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent, ch, readInterestOp);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioMessageUnsafe();
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (inputShutdown) {
            return;
        }
        super.doBeginRead();
    }

    private final class NioMessageUnsafe extends AbstractNioUnsafe {

        private final List<Object> readBuf = new ArrayList<Object>();

        @Override
        // 在 NioMessageUnsafe 的 read() 方 法 中 会 执 行 doReadMessages(此处用到了模板设计模式)。真正调用的是 AbstractNioMessageChannel
        // 的 子 类 NioServerSocketChannel 的 doReadMessages() 方 法 。 此 方 法 最 终 调 用 ServerSocketChannel 的 accept()方法，
        // 以获取接入的SocketChannel。将accept()方法在 AbstractNioChannel的构造方法中设置为非阻塞状态，不管是否有 Channel接入，
        // 都会立刻返回，并且一次最多默认获取16个，可以通过 设 置 option 参 数 MAX_MESSAGES_PER_READ 来 调 整 。 获 取 到 SocketChannel 后 ，
        // 构 建 NioSocketChannel ， 并 把 构 建 好 的 NioSocketChannel对象作为消息msg传送给Handler(此Handler是 ServerBootstrapAcceptor ) ，
        // 触 发 Pipeline 管 道 的 fireChannelRead()方法，进而触发read事件，最后会调用Handler的 channelRead()方法。
        public void read() {
            assert eventLoop().inEventLoop();
            // 获取Channel的配置对象
            final ChannelConfig config = config();
            final ChannelPipeline pipeline = pipeline();
            // 获取计算内存分配 Handle
            final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
            // 清空上次记录
            allocHandle.reset(config);

            boolean closed = false;
            Throwable exception = null;
            try {
                try {
                    do {
                        /***
                         * 调用子类doReadMessages()方法
                         * 读取数据包， 并放入readBuf链表中
                         * 当成功读取时返回1
                         */
                        int localRead = doReadMessages(readBuf);
                        // 已无数据，跳出循环
                        if (localRead == 0) {
                            break;
                        }
                        // 链路关闭，跳出循环
                        if (localRead < 0) {
                            closed = true;
                            break;
                        }
                        // 记录成功读取的次数
                        allocHandle.incMessagesRead(localRead);
                        // 默认循环不能超过16次
                    } while (allocHandle.continueReading());
                } catch (Throwable t) {
                    exception = t;
                }

                int size = readBuf.size();
                // 循环处理读取的数据包
                for (int i = 0; i < size; i ++) {
                    readPending = false;
                    // 触发channelRead事件
                    pipeline.fireChannelRead(readBuf.get(i));
                }
                readBuf.clear();
                // 记录当前读取记录，以便下次分配合理的内存
                allocHandle.readComplete();
                // 触发readComplete事件
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    // 处理Channel 异常关闭
                    closed = closeOnReadError(exception);

                    pipeline.fireExceptionCaught(exception);
                }

                if (closed) {
                    inputShutdown = true;
                    // 处理Channel正常关闭
                    if (isOpen()) {
                        close(voidPromise());
                    }
                }
            } finally {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                // 读操作完毕，且没有配置自动读
                if (!readPending && !config.isAutoRead()) {
                    // 移除读操作事件
                    removeReadOp();
                }
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        final SelectionKey key = selectionKey();
        // 获取Key的兴趣集
        final int interestOps = key.interestOps();

        for (;;) {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                // 数据已经全部发送完， 兴趣集中移除OP_WRITE事件
                if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                    key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
                }
                break;
            }
            try {
                boolean done = false;
                // 获取配置中循环写的最大次数
                for (int i = config().getWriteSpinCount() - 1; i >= 0; i--) {
                    // 调用子类方法，若msg写成功了， 则返回true
                    if (doWriteMessage(msg, in)) {
                        done = true;
                        break;
                    }
                }
                // 若发送成功，则从其缓存链表来移除
                // 继续发送下一个缓存节点数据
                if (done) {
                    in.remove();
                } else {
                    // Did not write all messages.
                    // 若没有写成功，则doWriteMessage返回false
                    if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                        // 将OP_WRITE事件添加到兴趣事件集中
                        key.interestOps(interestOps | SelectionKey.OP_WRITE);
                    }
                    break;
                }
            } catch (Exception e) {
                // 当出现异常时，判断是否继续写
                if (continueOnWriteError()) {
                    in.remove(e);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Returns {@code true} if we should continue the write loop on a write error.
     */
    protected boolean continueOnWriteError() {
        return false;
    }

    protected boolean closeOnReadError(Throwable cause) {
        if (!isActive()) {
            // If the channel is not active anymore for whatever reason we should not try to continue reading.
            return true;
        }
        if (cause instanceof PortUnreachableException) {
            return false;
        }
        if (cause instanceof IOException) {
            // ServerChannel should not be closed even on IOException because it can often continue
            // accepting incoming connections. (e.g. too many open files)
            return !(this instanceof ServerChannel);
        }
        return true;
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(List<Object> buf) throws Exception;

    /**
     * Write a message to the underlying {@link java.nio.channels.Channel}.
     *
     * @return {@code true} if and only if the message has been written
     */
    protected abstract boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception;
}
