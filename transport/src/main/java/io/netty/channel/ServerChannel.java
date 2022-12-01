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

import io.netty.channel.socket.ServerSocketChannel;

/**
 * A {@link Channel} that accepts an incoming connection attempt and creates
 * its child {@link Channel}s by accepting them.  {@link ServerSocketChannel} is
 * a good example.NIO服务器设计思维导图如图5-1所示，主要是关于编写一套NIO服 务器需要完成的一些基本步骤，
 * 主要分三步:
 * 第一步，服务器启动与 端口监听;
 * 第二步，ServerSocketChannel处理新接入链路;
 * 第三步， SocketChannel读/写数据。
 *
 *
 */
public interface ServerChannel extends Channel {
    // This is a tag interface.
}
