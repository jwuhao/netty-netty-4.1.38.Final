package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class NettyEchoClientHandler  extends ChannelInboundHandlerAdapter {


    /**
     * 出站处理方法
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg ;
        int len = byteBuf.readableBytes();
        byte [] arr = new byte[len];
        byteBuf.getBytes(0,arr);
        System.out.println("client received:" + (new String(arr,"UTF-8")));
        // 释放 ByteBuf 的两种方法
        // 方法一， 手动释放ByteBuf
        byteBuf.release();
        // 方法二，调用父类的入站方法，将msg 向后传递
        // super.channelRead(ctx,msg);

    }
}
