package com.tuling.nio.length.field.based.frame.decoder.example5;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class NettyServer {


    public static void main(String[] args) {
        // 创建两个线程组bossGroup 和workerGroup ， 含有的子线程NioEventLoop 的个数默认为CPU 核数的两倍
        // BossGroup只是处理连接请求，真正的和客户端业务处理，会交给workerGroup完成
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // 创建服务端的启动对象
            ServerBootstrap bootstrap = new ServerBootstrap();
            // 使用链式编程来配置参数
            bootstrap.group(bossGroup, workerGroup)//设置两个线程组
                    .channel(NioServerSocketChannel.class)              // 使用NioServerSocketChannel 作为服务器的通道实现
                    // 初始化服务器连接队列大小，服务端处理客户端连接请求是顺序处理的，所以同一时间，只能处理一个客户端连接，多个客户端同时来的时候
                    // 服务端将不能处理的客户端连接请求放在队列中等待处理
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // 对workerGroup 的SocketChannel设置处理器
                            // maxFrameLength : 发送的数据包最大长度， 发送数据包的最大长度，例如1024，表示一个数据包最多可发送1024个字节
                            // lengthFieldOffset: 长度字段的偏移量， 指的是长度字段位于数据包内部字节数组中的下标值
                            // lengthFieldLength: 长度字段自己占用的字节数，如果长度字段是一个int整数，则为4，如果长度字段是一个short整数，则为2
                            // lengthAdjustment: 长度字段的偏移量矫正， 这个参数最为难懂，在传输协议比较复杂的情况下，例如包含了长度字段，协议版本号， 魔数等
                            //                  那么解码时，就需要进行长度字段的矫正，长度矫正值的计算公式为：内容字段偏移量 - 长度字段偏移量 - 长度字段的字节数
                            //
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0 , 4,32,0 ));
                            ch.pipeline().addLast(new NettyServerHandler());
                        }
                    });
            System.out.println("netty server start ....");
            // 绑定一个商品并且同步，生成一个ChannelFuture异步对象，通过isDone()等方法可以判断异步事件的执行情况
            // 启动服务器（并绑定端口），bind是异步操作，sync方法是等待异步操作执行完毕
            ChannelFuture cf = bootstrap.bind(9000).sync();

            // 给注册监听器，监听我们关心的事件
            cf.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (cf.isSuccess()) {
                        System.out.println("监听端口9000成功");
                    } else {
                        System.out.println("监听端口9000失败");
                    }
                }
            });
            // 对通道关闭进行监听，closeFuture是异步操作，监听通道关闭
            // 通过sync方法同步等待通道关闭处理完毕，这里会阻塞等待通道关闭完成
            cf.channel().closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
