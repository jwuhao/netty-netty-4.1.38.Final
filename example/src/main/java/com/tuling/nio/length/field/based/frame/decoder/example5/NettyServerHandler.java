package com.tuling.nio.length.field.based.frame.decoder.example5;

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
        System.out.println("example3 服务器读取的线程 ：" + Thread.currentThread().getName());
        ByteBuf buf = (ByteBuf) msg;

        int length = buf.readInt();
        System.out.println("传送的数据长度为" + length);

        byte[] headers = new byte[32];
        buf.readBytes(headers);
        int headerLength = 0;
        for (int i = 0; i < 32; i++) {
            if (headers[i] < 0) {
                break;
            }
            headerLength++;
        }
        byte realyHeaders[] = new byte[headerLength];

        System.arraycopy(headers, 0, realyHeaders, 0, headerLength);
        System.out.println("头部数据： " + new String(realyHeaders, "utf-8"));

        byte[] bytes = new byte[buf.readableBytes()];
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
