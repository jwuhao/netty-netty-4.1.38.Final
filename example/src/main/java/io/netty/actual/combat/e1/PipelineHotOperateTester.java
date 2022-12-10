package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;

public class PipelineHotOperateTester {


    static class SimpleInHandlerA extends ChannelInboundHandlerAdapter{
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("入站处理器A : 被回调");
            super.channelRead(ctx, msg);
            // 从流水线删除当前业务处理器。
            ctx.pipeline().remove(this);
        }
    }



    static class SimpleInHandlerB extends ChannelInboundHandlerAdapter{
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("入站处理器B : 被回调");
            super.channelRead(ctx, msg);
        }
    }



    static class SimpleInHandlerC extends ChannelInboundHandlerAdapter{
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("入站处理器C : 被回调");
            super.channelRead(ctx, msg);
        }
    }

    public static void main(String[] args) {
        ChannelInitializer initializer = new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new SimpleInHandlerA());
                ch.pipeline().addLast(new SimpleInHandlerB());
                ch.pipeline().addLast(new SimpleInHandlerC());
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        // 第一次向通道中写入报文（或数据包）
        channel.writeInbound(buf);
        // 第二次向通道中写入报文 （或数据包）
        channel.writeInbound(buf);
        // 第三次向通道中写入报文（或数据包）
        channel.writeInbound(buf);
        try {
            Thread.sleep(Integer.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }



    // 入站处理器A : 被回调
    // 入站处理器B : 被回调
    // 入站处理器C : 被回调

    // 入站处理器B : 被回调
    // 入站处理器C : 被回调

    // 入站处理器B : 被回调
    // 入站处理器C : 被回调


}
