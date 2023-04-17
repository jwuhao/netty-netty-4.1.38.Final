package com.tuling.nio.length.field.based.frame.decoder.example1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;


// 自定义Handler需要继承netty 规定好的某个HandlerAdapter(规范)
public class NettyServerHandler extends ChannelInboundHandlerAdapter {


    /**
     * 读取客户端发送的数据
     *
     * @param ctx 上下文对象，含有通道channel ，管道 pipeline
     * @param msg 就是客户端发送的数据
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("服务器读取的线程 ：" + Thread.currentThread().getName());
        ByteBuf buf = (ByteBuf) msg;
        int length = buf.readInt();
        System.out.println("传送的数据长度为" + length);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        System.out.println("客户端发送的消息是： " + new String(bytes, "utf-8"));
    }


    /**
     * 数据读取完毕处理方法
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        System.out.println("=================channelReadComplete======================");
        ByteBuf buf = Unpooled.copiedBuffer("Hello Client", CharsetUtil.UTF_8);
        ctx.writeAndFlush(buf);
    }


    // 处理异常，一般需要关闭通道
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
