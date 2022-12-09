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

import java.net.SocketAddress;

/**
 * {@link ChannelHandler} which will get notified for IO-outbound-operations.
 * <p>
 * <p>
 * <p>
 * 通道出站处理器
 * 当业务处理完后，需要操作JavaNIO底层的通道时， 通过一系列的ChannelOutboundHandler通道出站处理器， 完成Netty 通道到底层通道的操作。
 * 比方说建立底层连接， 写入底层的JavaNIO 通道等，ChannelOutboundHandler接吕定义了大部分出站操作，如图6-10所示，具体的介绍如下
 */
public interface ChannelOutboundHandler extends ChannelHandler {
    /**
     * Called once a bind operation is made.
     *
     * @param ctx          the {@link ChannelHandlerContext} for which the bind operation is made
     * @param localAddress the {@link SocketAddress} to which it should bound
     * @param promise      the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception thrown if an error occurs
     * 监听地址（IP + 端口） 绑定，完成底层的JavaIO通道的IP地址绑定，如果使用了TCP传输协议，这个方法用于服务器端 。
     *
     */
    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception;

    /**
     * Called once a connect operation is made.
     *
     * @param ctx           the {@link ChannelHandlerContext} for which the connect operation is made
     * @param remoteAddress the {@link SocketAddress} to which it should connect
     * @param localAddress  the {@link SocketAddress} which is used as source on connect
     * @param promise       the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception thrown if an error occurs
     * 连接服务端：完成底层的JavaIO通道的服务端连接操作，如果使用TCP传输协议，这个方法用于客户端 。
     *
     */
    void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelPromise promise) throws Exception;

    /**
     * Called once a disconnect operation is made.
     *
     * @param ctx     the {@link ChannelHandlerContext} for which the disconnect operation is made
     * @param promise the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception thrown if an error occurs
     *  断开服务器连接，断开底层Java  IO 通道的服务端连接，如果使用TCP 传输协议，此方法主要用于客户端
     *
     */
    void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * Called once a close operation is made.
     *
     * @param ctx     the {@link ChannelHandlerContext} for which the close operation is made
     * @param promise the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception thrown if an error occurs
     * 主动关闭通道，判断底层的通道，例如，服务器端口的新连接监听通道。
     */
    void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * Called once a deregister operation is made from the current registered {@link EventLoop}.
     *
     * @param ctx     the {@link ChannelHandlerContext} for which the close operation is made
     * @param promise the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception thrown if an error occurs
     */
    void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * Intercepts {@link ChannelHandlerContext#read()}.
     * 从底层读数据，完成Netty 通道从Java IO 通道的数据读取
     */
    void read(ChannelHandlerContext ctx) throws Exception;

    /**
     * Called once a write operation is made. The write operation will write the messages through the
     * {@link ChannelPipeline}. Those are then ready to be flushed to the actual {@link Channel} once
     * {@link Channel#flush()} is called
     *
     * @param ctx     the {@link ChannelHandlerContext} for which the write operation is made
     * @param msg     the message to write
     * @param promise the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception thrown if an error occurs
     * 写数据到底层， 完成Netty 通道丗底层Java IO通道的数据写入操作，此方法仅仅是触发一下操作而已， 并不是完成实际的数据写入操作
     */
    void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception;

    /**
     * Called once a flush operation is made. The flush operation will try to flush out all previous written messages
     * that are pending.
     *
     * @param ctx the {@link ChannelHandlerContext} for which the flush operation is made
     * @throws Exception thrown if an error occurs
     * 腾空缓冲区中的数据，把这些数据写入到对端， 将底层缓冲区的数据腾空，立即写入到对端
     */
    void flush(ChannelHandlerContext ctx) throws Exception;
}
