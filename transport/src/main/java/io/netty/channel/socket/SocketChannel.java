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
package io.netty.channel.socket;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/**
 * A TCP/IP socket {@link Channel}.
 * 与普通BIO中的Socket一样，是连接到TCP网络 Socket上的Channel。它的创建方式有两种:一种方式与 ServerSocketChannel的创建方式类似，
 * 打开一个SocketChannel，这 种方式主要用于客户端主动连接服务器;另一种方式是当有客户端连 接到ServerSocketChannel上时，会创建一个SocketChannel。
 *
 *
 *NIO服务器设计思维导图如图5-1所示，主要是关于编写一套NIO服 务器需要完成的一些基本步骤，
 * 主要分三步:
 * 第一步，服务器启动与 端口监听;
 * 第二步，ServerSocketChannel处理新接入链路;
 * 第三步， SocketChannel读/写数据。
 *
 *                                                      |---> 创建多路复用器Selector
 *                                                      |---> 打开ServerSocketChannel
 *                  |--------> 服务器启动与端口监听------>| ---> 把ServerSocketChannel注册到Selector上
 *                  |                                   |----> 绑定监听端口
 *                  |                                   |----> 设置监听OP_ACCEPT事件
 *  NIO服务器------->|                                                  |---------> 循环遍历遍历多路利用器准备就绪的SelectionKey
 *                  |---------> ServerSocketChannel处理新接入链路------> |---------> 从SelectionKey 中获取ServerSocketChannel
 *                  |                                                  |---------> 通过ServerSocketChannel的accept获取接入的客户端链路SocketChannel
 *                  |                                                  |---------> 把客户端链路注册到Selector上，并监听其OP_READ事件
 *                  |
 *                  |                                              |--------> 循环遍历多路利用器准备就绪的SelectKey
 *                  |--------> SocketChannel 读/写数据------------> |--------> 从SelectionKey 中获取SocketChannel
 *                                                                 |--------> 从SocketChannel 中读取数据
 *                                                                 |--------> 解码，编码-------> 业务逻辑处理
 *                                                                 |--------> SocketChannel write结果
 *
 *  Netty服务的启动流程与图5-1中的NIO服务器启动的核心部分没什 么区别，同样需要创建Selector，不过它会开启额外线程去创建;同 样 需 要 打
 *  开 ServerSocketChannel ， 只 是 采 用 的 是 NioServerSocketChannel 来 进 行 包 装 ; 同 样 需 要 把 ServerSocketChannel注册到
 *  Selector上，只是这些事情都是在额外的 NioEventLoop线程上执行的，并返回ChannelPromise来异步通知是否 注册成功。ChannelPromise在之前
 *  的实战中运用过(Netty在多线程操 作中会大量使用)。Netty服务启动时涉及的类和方法如图5-2所示， 接下来通过几个问题，由浅入深地进行剖析。
 *
 */
public interface SocketChannel extends DuplexChannel {
    @Override
    ServerSocketChannel parent();

    @Override
    SocketChannelConfig config();
    @Override
    InetSocketAddress localAddress();
    @Override
    InetSocketAddress remoteAddress();
}
