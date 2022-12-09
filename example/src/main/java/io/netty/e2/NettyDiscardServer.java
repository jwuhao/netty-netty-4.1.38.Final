package io.netty.e2;

/**
 * create by 尼恩 @ 疯狂创客圈
 **/

import io.netty.actual.combat.e1.Logger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NettyDiscardServer {
    private final int serverPort;
    // 它的职责是一个组装和集成器，交地不同的Netty组装在一起，另外 ,ServerBootstrap能够按照应用声明的需要，为组件设置好对应的
    // 参数， 最后实现Netty服务器的监听和启动，服务启动类ServerBootstrap
    ServerBootstrap b = new ServerBootstrap();

    public NettyDiscardServer(int port) {
        this.serverPort = port;
    }

    public void runServer() {
        //创建reactor 线程组
        EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);                // 包工头
        EventLoopGroup workerLoopGroup = new NioEventLoopGroup();           // 工人

        try {
            //1 设置reactor 线程组
            b.group(bossLoopGroup, workerLoopGroup);
            //2 设置nio类型的channel
            b.channel(NioServerSocketChannel.class);
            //3 设置监听端口
            b.localAddress(serverPort);
            //4 设置通道的参数
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

            //5 装配子通道流水线
            b.childHandler(new ChannelInitializer<SocketChannel>() {
                //有连接到达时会创建一个channel
                protected void initChannel(SocketChannel ch) throws Exception {
                    // pipeline管理子通道channel中的Handler
                    // 向子channel流水线添加一个handler处理器
                    ch.pipeline().addLast(new NettyDiscardHandler());
                }
            });
            // 6 开始绑定server
            // 通过调用sync同步方法阻塞直到绑定成功
            ChannelFuture channelFuture = b.bind().sync();
            Logger.info(" 服务器启动成功，监听端口: " +
                    channelFuture.channel().localAddress());

            // 7 等待通道关闭的异步任务结束
            // 服务监听通道会一直等待通道关闭的异步任务结束
            ChannelFuture closeFuture = channelFuture.channel().closeFuture();
            closeFuture.sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 8 优雅关闭EventLoopGroup，
            // 释放掉所有资源包括创建的线程
            workerLoopGroup.shutdownGracefully();
            bossLoopGroup.shutdownGracefully();
        }

    }

    // 一个IO事件从操作系统底层产生后，在Reactor械中处理流程如下6-1所示
    // 整个流程大致分为如下4步， 具体如下
    // 第1步， 通道注册IO源于通道（Channel） ,IO是和通道（对应的底层连接而言）强相关的，一个IO 事件，一定属于某个通道，但是如果要查询通道的事件 》
    //  首先要将通道注册到选择器，只需要通道提前注册到Selector选择器即可， IO 事件会被选择器查询到。
    // 第2步， 查询选择，在反应器器上，一个反应器（或者SubReactor子反应器）会负责一个线程，不断的轮询，查询选择器中的IO事件(选择键)
    // 第3步： 事件分发，如果查询到IO事件，则分发给与IO事件有绑定关系的Handler业务处理器。
    // 第4步：完成真正的IO和业务处理， 这一步由Handler业务处理负责
    // 以上4步， 就是整个反应器模式的IO处理器流程， 其中，第1步和第2步， 其实就是Java   NIO的功能，反应器模式仅仅是利用了Java NIO 的优势而已.
    // 题外话： 上面流程比较重要，是学习Netty 的基础，如果这里看不懂， 作为铺垫，请先回到反应器模式的详细介绍的分部，回头再学习一下返回器模式 。
    // Netty 中的Channel通道组件
    // Channel 通道组件是Netty中非常重要的组件，为什么首先要说的是Channel 通道组件呢？ 原因是：反应器模式和通道紧密相关，反应器的查询和分发的
    // IO事件都来自于Channel 通道的组件 。
    // Netty 中不直接使用Java NIO的Channel通道组件，对Channel通道组件进行了自己的封装在Netty中，有一系列的Channel 通道组件，为了支持多种通信
    // 协议 ， 换句话说， 对于每一种通信连接协议，Netty 都实现了自己的通道 。
    // 另外一点就是，除了Java 的NIO ，Netty 还能处理Java 面向流的OIO (Old-IO)，即传统的阻塞式的IO  。
    // 总结起来，Netty 中的每和中协议的通道，都有NIO(异步 IO ) 和OIO(阻塞式IO) ，两处版本，对于不同的协议，Netty中常见的通道类型如下  :
    // NioSocketChannel : 异步非阻塞TCP Socket传输通道
    // NIOServerSocketChannel :  异步非阻塞TCP Socket 服务器端监听通道
    // NioDatagramChannel : 异步非阻塞UDP传输通道
    // NioSctpChannel : 异步非阻塞Sctp传输通道
    // NioSctpServerChannel : 异步非阻塞Sctp 服务器端监听通道
    // OioSocketChannel : 同步 阻塞式Tcp Socket 传输通道
    // OioServerSocketChannel :  同步阻塞式Tcp Socket 服务器监听通道
    // OioDatagramChannel : 同步阻塞式UDP传输通道
    // OioSctpChannel : 同步阻塞式Sctp传输通道
    // OioSctpServerChannel : 同步阻塞式Sctp服务器监听通道
    // 一般来说，服务编程用到的最多的通信协议还是TCP 协议 ， 对应的传输通道类型为NioChannelSocket类， 服务器监听类为NioServerSocketChannel
    // 在主要的使用方法上，其他的通道类型和这个NioSocketChannel 类的原理上基本上是相通的， 因此，本书很多的安全都以NioSocketChannel通道为主
    // 在Netty的NioSocketChannel 内部封装了一个Java Nio SelectableChannel 成员， 通过这个内部的JavaNio通道，Netty 的NioSocketChannel
    // 通道上的IO 操作，最终会落到Java NIo 的SElectableChannel 底层通道，NioSocketChannel的继承关系图。
    // 在反应器模式中，一个反应器（或者Subreactor反应器）会负责一个事件处理线程不断的轮询，通过Selector 选择器不断的查询注册过的IO 事件
    // 选择键， 如果查询到了IO 事件，则分发给Handler处理
    // Netty 中的反应器有多个实现类， 与Channel 通道有关系，对应于NioSocketChannel通道，Netty 的反应器为NioEventLoop
    // NioEventLoop类绑定两个重要的Java成员属性，一个是Thread 线程类的成员，一个是JavaNio 选择器的成员属性， NioEventLoop 的继承关系和主要的成员属性，如下图6-3所示 。
    // 通过这个关系图，可以看出 ，NioEventLoop和前面的章节讲到的反应器，在思路上是一致辞的，一个NioEventLoop拥有一个Thread 线程， 负责一个Java NIO Selector
    // 选择器IO 事件轮询 。
    // 在Netty 中，EventLoop 反应器和Netty Channel通道，关系如何呢？ 理论上来说，一个EventLoopNetty 返回器和NettyChannel通道是一个一对多的关系 。
    // 一个反应器可以注册成千上万的通道 .
    // Netty中的Handler处理器
    // 在前面的章节中，解读Java NIO的事件类型时讲到， 可供选择器监控的通道IO 事件类型包括以下4种
    // 可读：SelectionKey.OP_READ
   // 可写：SelectionKey.OP_WRITE
    // 连接 ： SelectionKey.OP_CONNECT
   // 接收 ： SelectionKey.OP_ACCEPT
    // 在Netty中，EventLoop 反应器内部有一个Java NIO 选择器成员，执行以上的事件查询，然后进行对应的事件分发，事件分发（Dispatch） 的
    // 目标就是Netty 自己的Handler处理器
    // Netty 的Handler 处理器分为两大类，第一类是ChannelInBoundHandler通道入站处理器，第二类是ChannelOutBoundHandler 通道出站处理器，
    // 二者都继承了ChannelHandler 处理器接口。 Netty 中的Handler 处理器的接口与继承之间的关系，如图6-5所示 。
    // Netty中的入站， 触发的方向为，从通道到ChannelInboundHandler 通道入站处理器。
    // Netty中的出站， 本来就包括Java NIO和OP_WRITE可写事件，注意，OP_WRITE可写事件是Java NIO的底层概念，它和Netty的出站处理的概念不是一个维度的。
    // Netty的出站处理是应用层维度的， 那么，Netty 中的出站处理器到通道的某个层次IO操作。 例如 ， 在应用程序完成业务处理后，可以通过
    // ChannelOutBoundHandler通道出站处理器将处理的结果写入到底层处理，它的最常用的一个方法就是write()方法，把数据写入到通道 .
    // 这两个业务处理接口都有各自的默认实现：ChannelInboundHandler的默认实现为ChannelInboundHandlerAdapter ， 叫作通道入站处理适配器
    // ChannelOutboundHandler 的默认实现为ChanneloutBoundHandlerAdapter，叫作通道出站处理适配器，这个默认的通道处理适配器，分别实现了入
    // 站操作和出站处理适配器， 这两个默认的通道处理适配器，分别实现了入站操作和出站操作的基本功能， 如果要实现自己的业务处理器，不需要从零
    // 分号实现了入站和出站操作的基本功能，如果要实现自己的业务上的处理， 不需要从零开始去处理器的接口，只需要继承通道处理适配器即可。
    // 6.2.5 Netty 的流水线（Pipeline）
    // 来梳理一下Netty的反应器模式中各个组件之间的关系 。
    // 1. 反应器（或者SubReactor子反应器）和通道之间是一对多的关系，一个反应器可以查询很多的通道的IO 事件 。
    // 2. 通道和Handler 处理器实例之间，是多对多的关系，一个通道的IO事件被多个Handler 实例处理， 一个Handler 处理器实例也能绑定到很多的通道 。
    // 处理多个通道的IO 事件 。
    // 问题是： 通道和Handler处理器实例之间的绑定关系，Netty是如何组织的呢？
    // Netty 设计了一个特殊的组件，叫作 ChannelPipeline ，通道流水线，它像一条管道 ， 将绑定到一个通道的多个Handler处理器实例，串在一起
    // 形成一条流水线，ChannelPipeline 通道流水线的默认实现，实际上被设计成一个双向链表，所有的Handler 处理器实例被包装成双向链表的节点 .
    //  被加入到ChannelPipeline(通道流水线)中
    // 重点申明 ： 一个Netty 通道拥有一条Handler处理器流水线，成员的名称叫作Pipeline
    // 问题来了，这里为什么将pipeline 翻译成流水线呢？ 而不是翻译成管道呢？ 还是有原因的。 具体来说，与流水线内部的Handler处理器之间的处理
    // IO 事件的先后次序有关。
    // 以入站处理为例， 每一个来自通道的IO事件，都会进入一次ChannelPipeline通道流水线，在进入第一个Handler处理器后， 这个IO事件将按照
    // 既定的从前往后的次序，在流水线不断的向后流动，流向下一个Handler处理器。
    // 在向后流动的过程中，会出现3种情况 。
    // 1. 如果后面还有其他的Handler 入站处理器， 那么IO 事件可以交给下一个Handler处理器向后流动。
    // 2. 如果后面没有其他的入站处理器， 这就意味着这个IO 事件在些次流水线中处理结束了。
    // 3. 如果在流水线中间需要终止流动，可以选择不将IO 事件交给下一个Handler处理器，流水线的执行也被终止了。
    // 为什么说Handler 的处理是按照既定的次序，而不是从前到后的次序呢？Netty 是这样规定入站处理器的，Handler的执行次序的。
    // 是从前到后， 出站处理器的执行次序，是从后到前，总之， IO 事件在流水线的执行次序 ， 与IO 事件的类型是有关系的。
    // 除了流动的方向与IO 操作的类型有关之外 ， 流动的过程中经过的处理器的节点类型，也是与IO 操作的类型有关，入站IO操作只支且只能从
    // InBound 入站处理器类型Handler流过，出站IO 操作只会且只能从OutBound出站处理器类型的Handler流过。
    // 总之，流水线的通道的大管家 ，为通道管理好它的一大堆Handler 小弟
    // 了解完流水线之后，大家应该对Netty 中的通道，EventLoop 反应器，Handler处理器，以及三者之间的协作关系，有一个比较清晰的
    // 认知和了解了，至此，大家基本可以运用开发简单的Netty 程序了，不过为了方便开发者，Netty 提供了一个类把上面的三个组件快速组装起来
    // 这个系列叫作Bootstrap 启动器，严格来说，不止一个类的名字为Bootstrap  ， 例如在服务器端的启动类叫ServerBootstrap 类
    // 下面，为大家详细的介绍这个提升开发效率的Bootstrap启动类。
    // 详解Bootstrap 启动器类
    // Bootstrap 类是Netty提供的一个便利的工厂类，可以通过它来完成 Netty的客户端或服务器端的Netty组件的组装，以及Netty 的初始化，当然
    // Netty的官方解释是，完全可以不用这个Bootstrap 启动器，但是，一点点的去手动创建通道，完成各种设置和启动，并且注册到EventLoop
    // 这个过程会非常麻烦 ， 通道情况下，还是使用这个便利的Bootstrap 工具类会效率更高。
    // 这两个启动器仅仅是使用的地方不同，它们大致的配置和使用方法都是相同的， 下面ServerBootStrap服务器启动类作为重点介绍对象 。
    // 在介绍ServerBootstrap的服务器启动流程之前，首先介绍，下一步涉及到两个基本的概念，父子通道，EventLoopGroup线程组
    // 事件循环线程组。
    // 6.3.1 父子通道。
    // 在在Netty中，每一个NioSocketChannel通道所封装的Java NIO 通道，再往下就是对应的操作系统底层的socket描述符， 理论上来说.
    // 操作系统底层的socket描述符分为两类。
    // 连接监听器类型，连接监听器类型的socket描述符，放在服务器端，它负责接收客户端的套接字连接，在服务器端有一个连接监听类型的socket描述符可以接受
    // (Accept)成上万的传输类的socket描述符。
    // 传输数据类型，数据传输类socket描述符负责传输数据，同一条TCP 的Socket传输链路，在服务器和客户端，都会有一个与之相对的数据传输类型的socket描述符
    // 在Netty 中，异步非阻塞服务端监听通道，NioServerSocketChannel ，封装在Linux底层描述符， 是连接监听类型，socket描述符,而NioSocketChannel
    // 异步非阻塞TCP Socket传输通道，封装在底层的Linux的描述符， 是数据传输类型的socket描述符。
    // 在Netty中，将有接收关系的NioServerSocketChannel和NioSocketChannel，叫作父子通道，其中，NioServerSocketChannel 负责服务器连接监听和接收 .
    // 也叫父通道（Parent Channel ）,对应于每一个接收到NioSocketChannel 传输类通道，也叫子通道（Child Channel）。
    // EventLoopGroup 线程组。
    // Netty 中的Reactor 反应器模式，肯定不是单线程版本的反应器模式，而是多线程版本的反应器模式，Netty的多线程版本的反应器模式是如何实现呢？
    //在Netty 中，一个EventLoop 相当于一个子反应器（SubReactor） ，大家已经知道，一个NioEventLoop 子反应器拥有一个线程， 同时拥有一个Java NIO
    // 选择器， Netty 如何组织外层的反应器呢？
    // 反过来说，Netty的EventLoopGroup 线程组是一个多线程版本的返回器， 而其中 的单个EventLoop 线程对应于一个反应器（SubReactor).
    // Netty的程序开发不会直接使用单个EventLoop 线程， 而是使用EventLoopGroup线程组， EventLoopGroup 的构造函数有一个参数.
    // 用于指定内部线程数，而在构造器初始化时，会按照传入的线程数量，在内部构造出多个Thread 线程和多个EventLoop 子反应器（一个线程对应一个
    // EventLoop 返回器），进行多个线程的IO 事件查询和分发。
    // Netty的程序开发不会直接使用单个EventLoop线程，而是使用EventLoopGroup线程组， EventLoopGroup 构造函数有一个参数，用于指定内部的线程数
    // 在构造器初始化时， 会按照传入的线程数量，在内部构造多个Thread 线程和多个EventLoop子返回器，一个线程对应一个EventLoop子反应器。
    // 进行多个线程的IO 事件查询和分发。
    // 如果使用EventLoopGroup的无参数的构造函数，没有传入线程数或传入的线程数为0 ， 那么EventLoopGroup内部的线程数到底是多少呢？默认的EventLoopGroup
    // 内部线程数为最大可用的CPU 处理器的数量的2倍，假设电脑使用的4核的CPU ， 那么在内部会启动8个EventLoop 线程， 相当于8个反应器SubReactor 为例子。
    // 从前文可以知，为了及时接受（Accept）到新的连接器，在服务器端， 一般有两个独立的返回器，一个反应器负责新连接的监听和接受，另一个反应器
    // 负责IO事件的处理，对应到Netty 服务器程序中，则是设置一代代个EventLoopGroup 线程组，一个EventLoopGroup 负责新连接监听和接受，一个EventLoopGroup
    // 负责IO事件处理。
    // 从前文可知，为了及时接受Accept到新连接，在服务端一般有两个独立的反应器，一个是反应器负责连接的监听和接受，另一个是返回器负责
    // IO 事件处理，对应的Netty 服务器程序中则是设置两个EventLoopGroup线程组，一个EventLoopGroup 负责新连接的监听和接受， 一个EventLoopGroup负责IO事件处理。
    // 那么，两个反应器如何分工呢？ 负责新连接的监听和接受的EventLoopGroup线程组，查询父通道的IO事件，有点像负责招工的包工头，因此，可以形象的称
    // 包工头，（Boss）线程组，另外一个是EventLoopGroup线程组负责查询所有的子通道的IO事件，并且执行Handler处理器中的业务处理，例如数据的
    // 输入和输出（有点像搬砖） ，这个线程组可以形象的称为工人， Worker 线程组。
    // 至此，已经介绍完两个基础的概念， 父子通道 ， EventLoopGroup 线程组， 下一节将介绍ServerBootstrap的启动流程。
    // 6.3.3 Bootstrap的启动流程。
    // Bootstrap 的启动流程，也就是Netty组件的组装配置，以及Netty服务器或者客户端的启动流程，在本节中对启动流程进行了梳理，大致分成了8个步骤
    // 本书仅仅演示的是服务端启动器的使用， 用到了启动器类为ServerBootstrap，正式使用前，首先创建一个服务端的启动器实例。
    // 创建一个服务端的启动器
    // ServerBootstrap b = new ServerBootstreap() ;
    // 接下来，结合前面的NettyDiscardServer 服务器程序代码，给大家详细的介绍一下Bootstrap 启动流程中精彩的8个步骤 。
    // 第1 步，创建反应器线程组，并赋值给ServerBootstrap 启动器的实例。
    // 创建反应器线程组
    // boss 线程组
    // EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
    // worker 线程组
    // EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
    // 1 设置反应器线程组
    // b.group(bossLoopGroup,workerLoopGroup);
    // 在设置反应器线程组之前， 创建了两个NioEventLoopGroup线程组，一个负责处理连接监听IO事件 ，名为bossLoopGroup : 另一个是负责数据IO
    // 事件和Handler 业务处理，名为workerLoopGroup ，在线程组创建完成后， 就可以配置给启动器实现了， 一次性的给启动器配置了两大线程组。
    //  不一定非得配置两个线程组，可以仅配置一个EventLoopGroup反应器线程组，具体的配置方法和调用b.group(workerGroup) ，在这种模式下
    // 连接监听IO事件和数据传输IO事件可能被挤在了同一个线程中处理， 这样会带来一个风险，新连接的接受被更加耗时的数据传输或者业务处理阻塞 .
    // 在服务器端， 建议在设置成两个线程组的工作模式 。
    // 第2步， 设置通道的IO事件类型。
    // Netty 不止支持JavaNIO ，也支持阻塞式的OIO (也叫BIO,Block-IO, 即阻塞式IO) 下面配置的是Java NIO类型的通道类型，方法如下
    // 2 . 设置Nio 类型的通道
    // b.channel(NioServerSocketChannel.class);
    // 如果确实需要指定Bootstrap的IO模式的BIO， 那这里配置上Netty的OioServerSocketChannel.class 类即可，由于Nio优势巨大 ，通常不会
    // 在Netty 中使用BIO 。
    // 第3步，设置监听端口
    // b.localAddress(new InetSocketAddress(port));
    // 这里最为简单的一步操作，主要 是设置服务器的监听地址
    // 第4步，设置传输通道的配置选项
    // b.option(ChannelOption.SO_KEEPALIVE,true);
    // b.option(ChannelOption.ALLOCATOR.PooledByteBufAllocator.DeFAULT);
    // 这里用到了Bootstrap的option()选项设置方法， 对于 服务器的Boostrap而言， 这个方法作用是，给父通道（ParentChannel）接收连接通道设置一些选项
    // 如果要给子通道（Child Channel） 设置一些通道选项， 则需要要另外一个childOption() 设置方法 。
    // 可以设置哪些通道选项（ChannelOption）呢？ 在上面代码中，设置了一个底层的TCP 相关的选项， ChannelOption.SO_KEEPALIV，选项表示
    // 是否开启TCP 底层心跳机制，true为开启，false为关闭。
    // 第5步，装配通道的Pipeline流水线
    // 上一节介绍到，每一个通道的子通道，都用一条ChannelPipeline流水线，它的内部有一又向链表，装配流水线的方式是， 将业务的处理器ChannelHandler 实例加入到双向链表中
    // 装配子通道的Handler流水线调用childHandler实例加入到双向链表中。
    // 装配子通道的Handler 流水线调用bhildHandler()方法，传递一个ChannelInitializer通道初始化类的实例， 在父通道成功接收到一个连接，并创建
    // 成功一个子通道后，就会初始化子通道，这里配置的ChannelInitializer实例就会被调用 。
    // 在ChannelInitializer通道初始化类的实例中，有一个InitChannel的初始化方法，在子通道创建后会被执行到，向子通道流水线 增加业务处理。
    // 5. 装配子通道流水线
    // b.childHandler(new ChannelInitializer<SocketChannel>(){
    //      // 有连接到达时会创建一个子通道，并初始化 。
    //      public void initChannel(SocketChannel ch ) throws Exception{
    //          // 流水线管理子通道中的Handler业务处理器, 向通道流水线 添加一个Handler 业务处理器
    //          ch.pipeline().addLast(new NettyDiscardHandler() );
    //      }
    // });
    // 为什么仅装配子通道的流水线，而不需要装配父通道的流水线呢？ 原因是， 父通道也就是NioServerSocketChannel 连接接受通道，它的内部业务处理是固定的
    // 在接受到新的链接后，创建子通道然后初始化子通道，所以不需要特别的配置，如果需要完成特殊的业务处理， 可以使用ServerBootstrap的
    // handler(ChannelHandler handler)方法，为父通道设置 ChannelInitializer 的初始化器。
    // 说明一下，ChannelInitlizer处理器有一个泛型参数SocketChannel ， 它代表需要初始化通道类型，这个类型需要和前面的启动器中设置的通道类型，一一对应起来 。
    // 第6步，开始绑定服务器新连接的监听端口
    // 6 开始绑定端口，通过调用sync同步方法直到绑定成功。
    // ChannelFuture channelFuture = b.bind.sync() ;
    // Logger.info("服务器启动成功， 监听端口：" + channelFuture.channel().localAddress());
    // 这个也很简单，b.bind()方法的功能，返回一个端口绑定Netty的异步任务channelFuture，在这里并没有给channelFuture异步任务增加了回调
    // 监听器， 而是阻塞的channelFuture的异步任务 ， 直到端口绑定任务执行完成 。
    // 在Netty中，所有的IO操作都是异步执行的， 这就意味着任何一个IO 操作会立刻返回，在返回的时候，异步任务还没有真正的执行，什么时候执行
    // 完成呢？Netty 的IO 操作，都会返回异步任务实例，如ChannelFuture实例， 通过自我阻塞一直到ChannelFuture异步任务执行完成 或者 为
    // ChannelFuture增加事件监听器的两种方式，以获取Netty中的IO操作真正结果，上面使用了第一种。
    // 至此，服务器正式启动。
    // 第7步，自我阻塞，直到通道关闭
    // ChannelFuture closeFuture = channelFuture.channel().closeFuture();
    // closeFuture.sync();
    // 如果要阻塞当前线程直到通道关闭，可以使用通道的closeFuture()方法，以获取通道关闭的异步任务，当通道被关闭时，closeFuture实例的sync()方法会返回 。
    // 第8步，关闭EventLoopGroup
    // 8 释放掉所有的资源，包括创建反应器线程
    // workerLoopGroup.shutdownGracefully();
    // bossLoopGroup.shutdownGracefully();
    // 关闭Reactor反应器线程组，同时会关闭内部的subReactor 子返回器线程，也会关闭内部的Selector选择器，内部软座线程以及负责查询的所有子
    // 通道，在子通道关闭后，会释放掉底层的资源 ， 如TCP Socket文件描述符等。
    // 6.3.4 ChannelOption通道选项
    // 无论是对于 NioServerSocketChannel 父通道类型，还是对于NioSocketChannel子通道类型，都可以设置一系列的ChannelOption选项，
    // 在ChannelOption类中定义了一大票通道选项。
    // 1.SO_RCVBUF ，SO_SNDBUF
   // 此为TCP 参数，每个TCPsocket （套接字）在内核都有一个发送缓冲区和一个接收缓冲区， 这两个选项就是用来设置TCP 连接的两个缓冲区大小
    // TCP 的全双工的工作模式以及TCP的滑动容器便是依赖于这两个独立的缓冲区及其填充的状态 。
    // 2. TCP_NODELAY
    // 此为TCP参数，表示立即发送数据，默认值为True （Netty的默认值为True,而操作系统的默认为false），该值用于设置Nagle算法的启用，该算法将小的
    // 碎片数据连接成更大的报文或数据包来最小化所发送的报文的数量，如果需要发送一些较小的报文，则需要禁用该算法，Netty默认禁用该算法，从而最小化报文
    // 报文传输的延时。
    // 3. SO_KEEPAVLVe
    // 此为TCP参数，表示底层TCP协议的心跳机制，true表示保持心跳，默认值为false,启用该功能时，TCP 会主动探测空闲连接的有效性，可以将此功能
    // 视为TCP 的心跳机制，需要注意的是，默认的心跳间隔是7200s 即2小时 ， Netty默认关闭该功能 。
    // 4. SO_REUSEADDR
    // 此为TCP参数，设置为true表示地址复用，默认值为false, 有四种情况需要用到这个参数设置 。
    // 当有一个有相同的本地地址和端口的socket1处于TIME_WAIT 状态时， 而我们希望启动的程序的socket2要占用该地址和端口，例如 在重启服务且保持先前端口时。
    // 有多块网卡或用IP Alias技术的机器在同一端口启动多个进程，但每个进程绑定的本地IP地址不能相同 。
    // 单个进程绑定相同的端口到多个socket（套接字）上，但每个socket绑定的IP地址不同。
    // 完全相同的地址和端口重复绑定，但这只用于UDP多播，不用于TCP
    // 5. SO_LINGER
    // 6.4 详解Channel 通道
    // 先介绍一下，使用了Channel通道的过程中所涉及的主要成员和方法，然后，为大家介绍一下Netty 所提供了一个专门的单元测试通道-EmbeddedChannel 嵌入式通道 .
    // 6.4.1 Channel通道的主要成员和方法
    // 在Netty 中，通道是其中的核心概念之一，代表着网络连接，表示是通信和主题，由它负责同对端进行网络 通信，可以写入数据到对端，也可以从
    // 对端读取数据
    // 通道的抽象类AbstractChannel的构造函数如下 ：
    //
    public static void main(String[] args) throws InterruptedException {
        int port = 8909;
        new NettyDiscardServer(port).runServer();
    }
}