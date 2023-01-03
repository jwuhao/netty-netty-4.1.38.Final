package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;

/**
 * create by 尼恩 @ 疯狂创客圈
 **/
@ChannelHandler.Sharable
public class NettyEchoServerHandler extends ChannelInboundHandlerAdapter {
    public static final NettyEchoServerHandler INSTANCE = new NettyEchoServerHandler();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf) msg;
        int len = in.readableBytes();
        byte[] arr = new byte[len];
        in.getBytes(0, arr);
        Logger.info("client received: " + new String(arr, "UTF-8"));

        ChannelFuture f = ctx.writeAndFlush(msg);
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                System.out.println("写回后，msg.refCnt:" + ((ByteBuf) msg).refCnt());
            }
        });
    }
}
