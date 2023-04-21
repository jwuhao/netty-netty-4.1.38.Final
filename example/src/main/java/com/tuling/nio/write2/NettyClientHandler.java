package com.tuling.nio.write2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

public class NettyClientHandler extends ChannelInboundHandlerAdapter {


    // 当客户端连接服务器完成就会触发这个方法
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        StringBuffer sb = new StringBuffer();
        for(int i = 0 ;i < 1023;i ++){
            sb.append("a");
        }
        sb.append("中");
        sb.append("bbbb");
        String sbString = sb.toString();
        byte[] midbytes = sbString.getBytes("UTF8");

        System.out.println("midbytes   = " + midbytes.length);
        ByteBuf buf = Unpooled.copiedBuffer("", CharsetUtil.UTF_8);
        buf.writeBytes(sb.toString().getBytes("utf-8"));
        ctx.writeAndFlush(buf);
    }


    // 当通道在读取事件时会触发，即服务端发送数据给客户端
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        System.out.println(" 收到服务端的消息： " + buf.toString(CharsetUtil.UTF_8));
        System.out.println("服务端的地址：" + ctx.channel().remoteAddress());
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
