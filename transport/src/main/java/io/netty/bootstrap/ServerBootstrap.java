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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * {@link Bootstrap} sub-class which allows easy bootstrap of {@link ServerChannel}
 *本节主要结合实际应用，联合第4章分析的核心组件，对Netty的 整体运行机制进行详细的剖析，主要分为两部分:第一部分，Netty服 务的启动过程及其
 * 内部线程处理接入Socket链路的过程;第二部分， Socket链路数据的读/写。本节主要通过普通Java NIO服务器的设计流 程来详细讲述Netty是如何运
 * 用NIO启动服务器，并完成端口监听以接 收客户端的请求的;以及它与直接使用NIO有哪些区别，有什么优点。 下面先看看NIO中几个比较重要的类。
 *
 *
 *
 *                                          | ----------> NioEventLoop.openSelector 创建多路复用器
 * ServerBootstrap辅助启动过程 ------------->|
 *                                          |                                                           |-------> channelFactory.newChannel 此处创建NioServerChannel实例，并打印ServerSocketChannel
 *                                          |                                                           |                                                                                             |---> setChannelOptions
 *                                          |                                                           |-------> init(初始化，设置Channel参数，将 ServerBootstrapAcceptor 准备加入到ChannelPipeline管道中) |---> ChannelPipeline.addLast
 *                                          |                                                           |
 *                                          |                           |------> initAndRegister----->  |                                       |-------> AbstractChannel.register()
 *                                          |                           |                               |                                       |-------> AbstractNioChannel.doRegister()
 *                                          |                           |                               |-------> NioEventLoop.register-------->|-------> 注册到Selector 上 NioServerSocketChannel.register
 *                                          |------> doBind(绑定接口)--->|                                                                       |-------> DefaultChannelPipeline.invokeHandlerAddedIfNeeded
 *                                          |                           |                                                                       |-------> 准备处理接入链接 ChannelPipeline.addLast(ServerBootstrapAcceptor)
 *                                          |                           |                               |-----> AbstractChannel.bind
 *                                          |                           |------> doBind(绑定接口)------->|-----> NioServverSocketChannel.doBind
 *                                                                      |                               |-----> ServerSocketChannel.bind
 *                                                                      |
 *                                                                      |                                                                                                                           |---->1.
 *                                                                      |                                                                                                                           |
 *                                                                      |                                                                                                                           |
 *                                                                      |-------> AbstractChannel.invokeLater(绑定端口后，最终设置监听OP_ACCEPT事件)------>DefaultChannelPipeline.fireChannelActive--> |
 *                                                                      |                                                                                                                           |
 *                                                                      |
 *
 *
 * Netty服务的启动主要分以下5步。
 * 1. 创建两个线程组，并调用父类 MultithreadEventExecutorGroup的构造方法实例化每个线程组的子线 程数组，Boss线程组只设置一条线程，
 * Worker线程组默认线程数为 Netty Runtime.availableProcessors()*2。在NioEventLoop线程创建的同时多路复用器Selector被开启(每条NioEventLoop线程都会开启 一个多路复用器)。
 * 2. 在 AbstractBootstrap 的 initAndRegister 中 ， 通 过 ReflectiveChannelFactory.newChannel() 来 反 射 创 建
 * NioServerSocketChannel对象。由于Netty不仅仅只提供TCP NIO服 务，因此此处使用了反射开启ServerSocketChannel通道，并赋值给 SelectableChannel的ch属性。
 * 3. 初始化NioServerSocketChannel、设置属性attr和参数 option，并把Handler预添加到NioServerSocketChannel的Pipeline管 道中。其中，
 * attr是绑定在每个Channel上的上下文;option一般用来 设置一些Channel的参数;NioServerSocketChannel上的Handler除了 包括用户自定义的，还会加上ServerBootstrapAcceptor。
 * 4. NioEventLoop线程调用AbstractUnsafe.register0()方法， 此方法执行NioServer SocketChannel的doRegister()方法。底层调用 ServerSocketChannel
 * 的 register() 方 法 把 Channel 注 册 到 Selector 上，同时带上了附件，此附件为NioServerSocketChannel对象本身。 此处的附件
 * attachment与第(3)步的attr很相似，在后续多路复用器 轮询到事件就绪的SelectionKey时，通过k.attachment获取。当出现 超时或链路未中断
 * 或移除时，JVM不会回收此附件。注册成功后，会调 用 DefaultChannelPipeline 的 callHandlerAddedForAllHandlers() 方 法，此方法会执
 * 行PendingHandlerCallback回调任务，回调原来在没 有注册之前添加的Handler。此处有点难以理解，在注册之前，先运行 了
 * Pipeline的addLast()方法。DefaultChannelPipeline的addLast() 方法的部分代码如下:
 *
 * 5. 注 册 成 功 后 会 触 发 ChannelFutureListener 的 operationComplete()方法，此方法会带上主线程的ChannelPromise参 数 ，
 *  然 后 调 用 AbstractChannel.bind() 方 法 ; 再 执 行 NioServerSocketChannel的doBind()方法绑定端口;当绑定成功后，
 *  会触发active事件，为注册到Selector上的ServerSocket Channel加 上监听OP_ACCEPT事件;最终运行ChannelPromise的safeSetSuccess()
 *  方法唤醒server Bootstrap.bind(port).sync()。
 *
 * 以上5步是对图5-2进行的详细说明，Netty服务的启动过程看起来 涉及的类非常多，而且很多地方涉及多线程的交互(有主线程，还有 EventLoop线程)。
 * 但由于NioServerSocketChannel通道绑定了一条 NioEventLoop线程，而这条NioEventLoop线程上开启了Selector多路 复用器，因此这些主要步
 * 骤的具体完成工作都会交给NioEventLoop线 程，主线程只需完成协调和初始化工作即可。主线程通过 ChannelPromise获取NioEventLoop线程的执
 * 行结果。这里有两个问题 需要额外思考。
 *
 * 1. ServerSocketChannel在注册到Selector上后为何要等到绑定端 口才设置监听OP_ACCEPT事件?提示:跟Netty的事件触发模型有关。
 * 2. NioServerSocketChannel 的 Handler 管 道 DefaultChannelPipeline是如何添加Handler并触发各种事件的?
 *
 * 这两个问题与Netty的架构设计有很大的关系，对于初学者来说， 有一定的难度，一定要跟着图5-2及以上5步多看几遍源码，多加思 考。
 *
 *
 */
public class ServerBootstrap extends AbstractBootstrap<ServerBootstrap, ServerChannel> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ServerBootstrap.class);

    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> childAttrs = new LinkedHashMap<AttributeKey<?>, Object>();
    private final ServerBootstrapConfig config = new ServerBootstrapConfig(this);
    private volatile EventLoopGroup childGroup;
    private volatile ChannelHandler childHandler;

    public ServerBootstrap() { }

    private ServerBootstrap(ServerBootstrap bootstrap) {
        super(bootstrap);
        childGroup = bootstrap.childGroup;
        childHandler = bootstrap.childHandler;
        synchronized (bootstrap.childOptions) {
            childOptions.putAll(bootstrap.childOptions);
        }
        synchronized (bootstrap.childAttrs) {
            childAttrs.putAll(bootstrap.childAttrs);
        }
    }

    /**
     * Specify the {@link EventLoopGroup} which is used for the parent (acceptor) and the child (client).
     */
    @Override
    public ServerBootstrap group(EventLoopGroup group) {
        return group(group, group);
    }

    /**
     * Set the {@link EventLoopGroup} for the parent (acceptor) and the child (client). These
     * {@link EventLoopGroup}'s are used to handle all the events and IO for {@link ServerChannel} and
     * {@link Channel}'s.
     */
    public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        super.group(parentGroup);
        ObjectUtil.checkNotNull(childGroup, "childGroup");
        if (this.childGroup != null) {
            throw new IllegalStateException("childGroup set already");
        }
        this.childGroup = childGroup;
        return this;
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they get created
     * (after the acceptor accepted the {@link Channel}). Use a value of {@code null} to remove a previous set
     * {@link ChannelOption}.
     */
    public <T> ServerBootstrap childOption(ChannelOption<T> childOption, T value) {
        ObjectUtil.checkNotNull(childOption, "childOption");
        if (value == null) {
            synchronized (childOptions) {
                childOptions.remove(childOption);
            }
        } else {
            synchronized (childOptions) {
                childOptions.put(childOption, value);
            }
        }
        return this;
    }

    /**
     * Set the specific {@link AttributeKey} with the given value on every child {@link Channel}. If the value is
     * {@code null} the {@link AttributeKey} is removed
     */
    public <T> ServerBootstrap childAttr(AttributeKey<T> childKey, T value) {
        ObjectUtil.checkNotNull(childKey, "childKey");
        if (value == null) {
            childAttrs.remove(childKey);
        } else {
            childAttrs.put(childKey, value);
        }
        return this;
    }

    /**
     * Set the {@link ChannelHandler} which is used to serve the request for the {@link Channel}'s.
     */
    public ServerBootstrap childHandler(ChannelHandler childHandler) {
        this.childHandler = ObjectUtil.checkNotNull(childHandler, "childHandler");
        return this;
    }

    @Override
    void init(Channel channel) throws Exception {
        final Map<ChannelOption<?>, Object> options = options0();
        synchronized (options) {
            setChannelOptions(channel, options, logger);
        }

        final Map<AttributeKey<?>, Object> attrs = attrs0();
        synchronized (attrs) {
            for (Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                @SuppressWarnings("unchecked")
                AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
                channel.attr(key).set(e.getValue());
            }
        }

        ChannelPipeline p = channel.pipeline();

        final EventLoopGroup currentChildGroup = childGroup;
        final ChannelHandler currentChildHandler = childHandler;
        final Entry<ChannelOption<?>, Object>[] currentChildOptions;
        final Entry<AttributeKey<?>, Object>[] currentChildAttrs;
        synchronized (childOptions) {
            currentChildOptions = childOptions.entrySet().toArray(newOptionArray(0));
        }
        synchronized (childAttrs) {
            currentChildAttrs = childAttrs.entrySet().toArray(newAttrArray(0));
        }

        p.addLast(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                ChannelHandler handler = config.handler();
                if (handler != null) {
                    pipeline.addLast(handler);
                }

                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.addLast(new ServerBootstrapAcceptor(
                                ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                    }
                });
            }
        });
    }

    @Override
    public ServerBootstrap validate() {
        super.validate();
        if (childHandler == null) {
            throw new IllegalStateException("childHandler not set");
        }
        if (childGroup == null) {
            logger.warn("childGroup is not set. Using parentGroup instead.");
            childGroup = config.group();
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private static Entry<AttributeKey<?>, Object>[] newAttrArray(int size) {
        return new Entry[size];
    }

    @SuppressWarnings("unchecked")
    private static Map.Entry<ChannelOption<?>, Object>[] newOptionArray(int size) {
        return new Map.Entry[size];
    }

    private static class ServerBootstrapAcceptor extends ChannelInboundHandlerAdapter {

        private final EventLoopGroup childGroup;
        private final ChannelHandler childHandler;
        private final Entry<ChannelOption<?>, Object>[] childOptions;
        private final Entry<AttributeKey<?>, Object>[] childAttrs;
        private final Runnable enableAutoReadTask;

        ServerBootstrapAcceptor(
                final Channel channel, EventLoopGroup childGroup, ChannelHandler childHandler,
                Entry<ChannelOption<?>, Object>[] childOptions, Entry<AttributeKey<?>, Object>[] childAttrs) {
            this.childGroup = childGroup;
            this.childHandler = childHandler;
            this.childOptions = childOptions;
            this.childAttrs = childAttrs;

            // Task which is scheduled to re-enable auto-read.
            // It's important to create this Runnable before we try to submit it as otherwise the URLClassLoader may
            // not be able to load the class because of the file limit it already reached.
            //
            // See https://github.com/netty/netty/issues/1328
            enableAutoReadTask = new Runnable() {
                @Override
                public void run() {
                    channel.config().setAutoRead(true);
                }
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        // 在ServerBootstrapAcceptor的channelRead()方法中，把 NioSocketChannel注册到Worker线程上，同时绑定Channel的Handler 链。
        // 这与5.1节中将NioServerSocketChannel注册到Boss线程上类似， 代码流程基本上都一样，只是实现的子类不一样，如后续添加的事件
        // 由OP_ACCEPT换成了OP_READ。通过这一步的分析，读者可以思考， Netty为何要把Channel抽象化?
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            final Channel child = (Channel) msg;

            child.pipeline().addLast(childHandler);

            setChannelOptions(child, childOptions, logger);

            for (Entry<AttributeKey<?>, Object> e: childAttrs) {
                child.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }

            try {
                // 这个方法负责对创建后的链接执行如下语句完成注册
                childGroup.register(child).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            forceClose(child, future.cause());
                        }
                    }
                });
            } catch (Throwable t) {
                forceClose(child, t);
            }
        }

        private static void forceClose(Channel child, Throwable t) {
            child.unsafe().closeForcibly();
            logger.warn("Failed to register an accepted channel: {}", child, t);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final ChannelConfig config = ctx.channel().config();
            if (config.isAutoRead()) {
                // stop accept new connections for 1 second to allow the channel to recover
                // See https://github.com/netty/netty/issues/1328
                config.setAutoRead(false);
                ctx.channel().eventLoop().schedule(enableAutoReadTask, 1, TimeUnit.SECONDS);
            }
            // still let the exceptionCaught event flow through the pipeline to give the user
            // a chance to do something with it
            ctx.fireExceptionCaught(cause);
        }
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public ServerBootstrap clone() {
        return new ServerBootstrap(this);
    }

    /**
     * Return the configured {@link EventLoopGroup} which will be used for the child channels or {@code null}
     * if non is configured yet.
     *
     * @deprecated Use {@link #config()} instead.
     */
    @Deprecated
    public EventLoopGroup childGroup() {
        return childGroup;
    }

    final ChannelHandler childHandler() {
        return childHandler;
    }

    final Map<ChannelOption<?>, Object> childOptions() {
        return copiedMap(childOptions);
    }

    final Map<AttributeKey<?>, Object> childAttrs() {
        return copiedMap(childAttrs);
    }

    @Override
    public final ServerBootstrapConfig config() {
        return config;
    }
}
