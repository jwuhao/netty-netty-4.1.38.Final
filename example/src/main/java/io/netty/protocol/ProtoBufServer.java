package io.netty.protocol;

import io.netty.actual.combat.e1.Logger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;

/**
 * create by 尼恩 @ 疯狂创客圈
 * 为了清晰的演示Probobuf传输，下面设计了一个简单的客户端，服务器传输程序，服务器接收客户端的数据包，并解码成Protobuf的POJO ，客户端将
 * Protobuf的POJO 编码成二进制数据包， 再发送到服务器端。
 *
 * 在服务器端， Protobuf 协议的解码过程如下 ：
 * 先使用Netty内置的ProtobufVarint32FrameDecoder ,根据varint32 格式可变长度值，从入站的数据包中解码出二进制 Probobuf字节码，然后可以使用Netty
 * 内置的ProtobufDecoder解码器将字节三成Protobuf 字节码，最后，自定义一个ProtobufBussinessDecoder解码器业处理Protobuf POJO 对象 。
 *
 **/
public class ProtoBufServer {

    private final int serverPort;
    ServerBootstrap b = new ServerBootstrap();

    public ProtoBufServer(int port) {
        this.serverPort = port;
    }

    public void runServer() {
        //创建reactor 线程组
        EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerLoopGroup = new NioEventLoopGroup();

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
                    // 向子channel流水线添加3个handler处理器
                    ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                    ch.pipeline().addLast(new ProtobufDecoder(MsgProtos.Msg.getDefaultInstance()));
                    ch.pipeline().addLast(new ProtobufBussinessDecoder());
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

    //服务器端业务处理器
    static class ProtobufBussinessDecoder extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            MsgProtos.Msg protoMsg = (MsgProtos.Msg) msg;
            //经过pipeline的各个decoder，到此Person类型已经可以断定
            Logger.info("收到一个 MsgProtos.Msg 数据包 =》");
            Logger.info("protoMsg.getId():=" + protoMsg.getId());
            Logger.info("protoMsg.getContent():=" + protoMsg.getContent());
        }
    }


    public static void main(String[] args) throws InterruptedException {
        int port = 8080;
        new ProtoBufServer(port).runServer();
    }


}