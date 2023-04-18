package com.tuling.nio.length.field.based.frame.decoder.example7;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

public class NettyClientHandler extends ChannelInboundHandlerAdapter {


    // 当客户端连接服务器完成就会触发这个方法
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        ByteBuf buf = Unpooled.copiedBuffer("", CharsetUtil.UTF_8);


        byte headers []  = getBytesInit(20,(byte)-1);

        byte[] src = "Accept: text/html".getBytes("utf-8");
        System.arraycopy(src, 0, headers, 0, src.length);


        byte[] midbytes = "HELLO, WORLD".getBytes("UTF8");

        byte[] applicationJson = getBytesInit(32, (byte) -1);

        byte[] jsonSrc = "application json".getBytes("utf-8");
        System.arraycopy(jsonSrc, 0, applicationJson, 0, jsonSrc.length);


        buf.writeBytes(headers);
        buf.writeInt(headers.length + midbytes.length + applicationJson.length + 4 );
        buf.writeBytes(applicationJson);
        buf.writeBytes(midbytes);
        ctx.writeAndFlush(buf);
    }

    public static byte [] getBytesInit(int count , byte defaultValue){
        byte applicationJson [] = new byte[count];
        for(int i = 0; i < applicationJson.length; i++) {
            applicationJson[i] = defaultValue;
        }
        return applicationJson;
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
