package io.netty.echoServer;

import io.netty.actual.combat.e1.Logger;
import io.netty.actual.combat.e1.NettyEchoClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.charset.Charset;

/**
 **/
public class NettyDumpSendClient {



    // 我们开发一引起远程的过程调用RPC的程序时， 通常会涉及到对象的序列化和反序列化问题， 例如 一个Person 对象从客户端通过TCP方式发送到
    // 服务器端， 因为TCP协议 UDP等这种低层协议，只能发送字节流， 所以需要应用层将Java POJO对象的序列化成字节流， 数据接收端再反序列化成
    // Java POJO对象即可， 序列化， 一定会涉及编码和格式化，目前我们可以选择编码方式有
    // 使用JSON , 将Java POJO 对象转换成JSON 结构化字符串，基于HTTP协议，在Web 应用移动开发方面等， 这是常用的编码方式，因为JSON 的可读性较长。
    // 但是它的性能稍差。
    // 基于XML ，和JSON一样， 数据在序列化成字节流之前都转换成字符串， 可读性强，性能差，异构系统 ，Open API 类型的应用中常用 。
    // 使用Java 内置的编码和序列化机制，可移植性强， 性能稍差 ， 无法跨平台 。
    // 其他的开源的序列化反序列化框架，例如 Apache Avro ,Apache Thrift , 这两个框架和ProtoBuf相比，性能非常的接近， 而且设计原理一样
    // 其中的Avro 在大数据存储 RPC 数据交换，本地存储 ， 时比较常用 ， Thrift 的亮点在于内置了RPC 机制，所以在开发一些RPC交互式应用时。
    // 客户端和服务器端的开发与部署非常的简单。

    // 如何选择序列化和反序列化框架呢？
    // 评价一个序列化框架的优缺，大概从两方面着手 。
    // 1. 结果数据大小，原则上说，序列化后的数据尺寸越小， 传输的效率越高。
    // 2. 结构复杂度，这会影响序列化反序列化的效率，结构越复杂，越耗时 。
    // 理论上来说，对于可性能要求不太高的服务器程序，可以选择JSON系统的序列化框架，对于性能要求比较高的服务器程序，则应该选择传输效率更高的
    // 二进制序列化框架，目前的建议是Protobuf
    //
    private int serverPort;
    private String serverIp;
    Bootstrap b = new Bootstrap();

    public NettyDumpSendClient(String ip, int port) {
        this.serverPort = port;
        this.serverIp = ip;
    }

    public void runClient() {
        //创建reactor 线程组
        EventLoopGroup workerLoopGroup = new NioEventLoopGroup();

        try {
            //1 设置reactor 线程组
            b.group(workerLoopGroup);
            //2 设置nio类型的channel
            b.channel(NioSocketChannel.class);
            //3 设置监听端口
            b.remoteAddress(serverIp, serverPort);
            //4 设置通道的参数
            b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

            //5 装配子通道流水线
            b.handler(new ChannelInitializer<SocketChannel>() {
                //有连接到达时会创建一个channel
                protected void initChannel(SocketChannel ch) throws Exception {
                    // pipeline管理子通道channel中的Handler
                    // 向子channel流水线添加一个handler处理器
                    ch.pipeline().addLast(NettyEchoClientHandler.INSTANCE);
                }
            });
            ChannelFuture f = b.connect();
            f.addListener((ChannelFuture futureListener) ->
            {
                if (futureListener.isSuccess()) {
                    Logger.info("EchoClient客户端连接成功!");

                } else {
                    Logger.info("EchoClient客户端连接失败!");
                }

            });

            // 阻塞,直到连接完成
            f.sync();
            Channel channel = f.channel();

            //6发送大量的文字
            byte[] bytes = "疯狂创客圈：高性能学习社群!".getBytes(Charset.forName("utf-8"));
            for (int i = 0; i < 1000; i++) {
                //发送ByteBuf
                ByteBuf buffer = channel.alloc().buffer();
                buffer.writeBytes(bytes);
                channel.writeAndFlush(buffer);
            }


            // 7 等待通道关闭的异步任务结束
            // 服务监听通道会一直等待通道关闭的异步任务结束
            ChannelFuture closeFuture =channel.closeFuture();
            closeFuture.sync();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 优雅关闭EventLoopGroup，
            // 释放掉所有资源包括创建的线程
            workerLoopGroup.shutdownGracefully();
        }

    }

    public static void main(String[] args) throws InterruptedException {
        int port = 8080;
        String ip = "localhost";
        new NettyDumpSendClient(ip, port).runClient();
    }
}