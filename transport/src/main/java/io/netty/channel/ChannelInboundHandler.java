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

/**
 * {@link ChannelHandler} which adds callbacks for state changes. This allows the user
 * to hook in to state changes easily.
 *
 *
 *  在Reactor反应咕咕经典的模型中， 反应器查询到IO事件后，分发到Handler 业务处理器， 由Handler 完成 IO 操作的业务处理。
 *  整个IO 的处理操作环境包括，从通道读数据包，数据包解码 业务处理，电影票数据编码，把数据包写到通道，然后由通道发送到对端 。
 *  如图6-8所示 。
 *  前面两环节从通道读数据包和由通道发送到对端 。 由Netty 底层负责完成 ，不需要用户程序负责。
 *  用户程序主要在Handler业务处理中，Handler涉及的环节为：数据包解码，业务处理，目标数据编码，把数据包写到通道中。
 *  前面已经介绍过， 从应用 程序开发人员的角度来看，有入站和出站两种类型的操作。
 *  入站处理：触发的方向为，自底向上， Netty的内部如通道到channelInboundHandler 入站处理器。
 *  出站处理，触发的方向为，自顶向下，从 ChanneloutBoundHandler 出站处理器到Netty内部如通道中。
 *  按照这种方向来分，前面数据包解码，业务处理两个环节，属于入站处理器的工作，后面目标数据编码，把数据包写到通道中两个环节，属于出站处理器的工作
 *
 *  ChannelInboundHandler 通道入站处理器
 *  当数据或者信息入站到Netty通道时，Netty将触发入站处理器 ChannelInboundHandler对应的入站API ，进行入站操作处理。
 *
 */
public interface ChannelInboundHandler extends ChannelHandler {

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered with its {@link EventLoop}
     * 当通道注册完成后，Netty 会调用fireChannelRegistered ，触发通道注册事件，通道会启动该入站操作的流水线处理。 在通道注册过的
     * 入站处理器Handler的channelRegistered方法， 会被调用到。
     */
    void channelRegistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was unregistered from its {@link EventLoop}
     */
    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} is now active
     * 当通道激活完成后，Netty 会调用fireChannelActive()方法，激发通道激活事件，通道会启动该入站操作的流水线处理， 在通道注册过的入站
     * 处理器Handler的channelActive()方法，会被调用到。
     *
     */
    void channelActive(ChannelHandlerContext ctx) throws Exception;

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered is now inactive and reached its
     * end of lifetime.
     * 当连接被断开或者不可用时，Netty会调用fireChannelInactivite触发连接不可用事件，通道会启动对应的流水线处理，在通道注册过的入让处理器Handler
     * 的ChannelInactive方法，会被调用到 。
     */
    void channelInactive(ChannelHandlerContext ctx) throws Exception;

    /**
     * Invoked when the current {@link Channel} has read a message from the peer.
     * 当通道缓冲区可读，Netty 会调用fireChannelRead，触发通道激活事件，通道会启动该站操作的流水线处理，在通道注册过的入站处理器Handler的
     * channelRead方法，被调用到。
     */
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * Invoked when the last message read by the current read operation has been consumed by
     * {@link #channelRead(ChannelHandlerContext, Object)}.  If {@link ChannelOption#AUTO_READ} is off, no further
     * attempt to read an inbound data from the current {@link Channel} will be made until
     * {@link ChannelHandlerContext#read()} is called.
     * 当通道缓冲区读完，Netty 会调用fireChannelReadComplete，触发通道读完事件，通道会启动该操作的流水线处理， 在通道注册过的入站处理器
     * Handler的channelReadComplete方法，会被调用到。
     *
     */
    void channelReadComplete(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if an user event was triggered.
     */
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;

    /**
     * Gets called once the writable state of a {@link Channel} changed. You can check the state with
     * {@link Channel#isWritable()}.
     */
    void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if a {@link Throwable} was thrown.
     */
    @Override
    @SuppressWarnings("deprecation")
    // 当通道处理过程发生异常时， Netty 会调用fireExceptionCaught，触发异常捕获事件，通道会启动异常捕获的流水线处理，在通道注册过的处理器Handler
    // 的exceptionCaught方法，会被调用到，注意 ，这个方法是在通道处理器中ChannelHandler定义的方法，入让的处理器，出站的处理器接口都继承这个方法
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}
