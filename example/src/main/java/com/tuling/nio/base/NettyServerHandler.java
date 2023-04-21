package com.tuling.nio.base;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;


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
        //Channel channel = ctx.channel();
        //ChannelPipeline pipeline = ctx.pipeline(); //本质是一个双向链接, 出站入站
        //将 msg 转成一个 ByteBuf，类似NIO 的 ByteBuffer
        ByteBuf buf = (ByteBuf) msg;
        System.out.println("收到客户端的消息:" + buf.toString(CharsetUtil.UTF_8));
        ctx.fireChannelRead(msg);
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
