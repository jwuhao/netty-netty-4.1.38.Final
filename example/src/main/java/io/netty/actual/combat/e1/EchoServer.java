package io.netty.actual.combat.e1;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

public class EchoServer {



    // NettyEchoServer 回显服务器的服端
    // 前面实现过Java NIO 版本的EchoServer回显服务器，在学习了Netty后，这里为大家设计和实现了一个Netty版本的EchoServer回显服务器
    // 功能很简单，从服务器端读取到客户端输入的数据,然后将数据直接回显到Console控制台。
    // 首先是服务器端的实践案例，目标为了掌握以下知识
    // 1. 服务端ServerBoostrap的装配和使用
    // 2. 服务器端NettyEchoServerHandler 入站处理器channnelRead入站处理方法的编写
    // 3. Netty的ByteBuf缓冲区的读取，写入， 以及ByteBuf 的引用计数的查看
    //
    public static void main(String[] args) {
        EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerLoopGroup = new NioEventLoopGroup();


    }


}
