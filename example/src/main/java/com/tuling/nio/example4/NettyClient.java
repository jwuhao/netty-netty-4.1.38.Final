package com.tuling.nio.example4;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class NettyClient {


    public static void main(String[] args) {
        // 客户端需要一个事件循环组
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // 创建客户端启动对象
            // 注意，客户端使用的不是ServerBootstrap ， 而是Bootstrap
            Bootstrap bootstrap = new Bootstrap();
            // 设置相关的参数
            bootstrap.group(group)                                  //设置线程组
                    .channel(NioSocketChannel.class)                  // 使用NioSocketChannel作为客户端的通道实现
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new NettyClientHandler());
                        }
                    });
            System.out.println("netty client start ");
            // 启动客户端去连接服务器端
            ChannelFuture channelFuture = bootstrap.connect("127.0.0.1",9000).sync();
            // 对关闭通道进行监听
            channelFuture.channel().closeFuture().sync();
        }catch (Exception e ){
            e.printStackTrace();
        }finally {
            group.shutdownGracefully();
        }
    }
}
