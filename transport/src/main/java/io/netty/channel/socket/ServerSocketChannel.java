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

import io.netty.channel.ServerChannel;

import java.net.InetSocketAddress;

/**
 * A TCP/IP {@link ServerChannel} which accepts incoming TCP/IP connections.
 *
 *
 *
 *
 * ServerSocketChannel:与普通BIO中的ServerSocket一样，主 要用来监听新加入的TCP连接的通道，而且其启动方式与ServerSocket
 * 的启动方式也非常相似，只需要在开启端口监听之前，把 ServerSocketChannel注册到Selector上，并设置监听OP_ACCEPT事件 即可。
 *
 *
 *
 *
 *
 *
 *
 *
 */
public interface ServerSocketChannel extends ServerChannel {
    @Override
    ServerSocketChannelConfig config();
    @Override
    InetSocketAddress localAddress();
    @Override
    InetSocketAddress remoteAddress();
}
