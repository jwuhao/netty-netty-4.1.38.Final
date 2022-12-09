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
package io.netty.util.concurrent;

import java.util.EventListener;

/**
 * Listens to the result of a {@link Future}.  The result of the asynchronous operation is notified once this listener
 * is added by calling {@link Future#addListener(GenericFutureListener)}.
 *
 *
 * 之前提到，和Guava的FutureCallback一样，Netty新增加了一相接口来封装异步非阻塞的回调的逻辑中，它就是GenericFutureListener接口。
 *
 */
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    /**
     * Invoked when the operation associated with the {@link Future} has been completed.
     *
     * @param future  the source {@link Future} which called this callback
     *
     *
     */
    // 监听器的回调方法
    // GenericFutureListener拥有一个回调方法，operationComplete,表示异步任务操作完成，在Future异步任务执行完后，将回调此方法，在大多
    // 在大多数情况下，Netty 的异步回调代码编写在GenericFutureListener 接口的实现类的operationComplete方法中。
    // 说明一下，GenericFutureListener 的父接口EventListener 是一个空接口， 没有任何的抽象方法，是一个仅仅具有标识作用的接口。
    void operationComplete(F future) throws Exception;
}
