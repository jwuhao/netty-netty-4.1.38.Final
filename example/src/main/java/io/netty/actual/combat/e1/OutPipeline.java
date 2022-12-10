package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;

public class OutPipeline {


    static class  SimpleOutHandlerA extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            System.out.println("出站处理器A: 被回调");
            super.write(ctx, msg, promise);
        }
    }


    static class SimpleOutHandlerB extends ChannelOutboundHandlerAdapter{
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            System.out.println("出站处理器B : 被回调");
            super.write(ctx, msg, promise);
        }
    }

    static class  SimpleOutHandlerC extends ChannelOutboundHandlerAdapter{
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            System.out.println("出站处理器C : 被回调");
            super.write(ctx, msg, promise);
        }
    }

    public static void main(String[] args) {
        ChannelInitializer initializer = new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new SimpleOutHandlerA());
                ch.pipeline().addLast(new SimpleOutHandlerB());
                ch.pipeline().addLast(new SimpleOutHandlerC());
            }
        };


        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        // 向通道写一个出站报文 （或数据包）
        channel.writeInbound(buf);
        try {
            Thread.sleep(Integer.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    // write()方法中，打印当前Handler 业务处理器的信息， 然后调用父类的write()方法，而这里的父类的write()方法会自动调用一下outBoundHandler()
    // 站处理器的write()方法，这里有个不小的问题， 在OutBoundHandler出站处理器的write()方法中，如果不调用父类的write()方法，结果会如何呢？
    // 出站处理器C : 被回调
    // 出站处理器B : 被回调
    // 出站处理器A : 被回调
    // 在代码中，通过pipeline.addLast()方法添加OutBoundHandler出站处理器的顺序为A->B->C ， 从结果可以看出， 出站流水处理次序从后向前
    // C->B->A ，最后加入到出站处理器，反而执行在最前面，这一点和InBound入站处处理的顺序刚好相反 。
    // 具体如下图6-12所示 。
    // 如何截断出站处理器呢？结论是，出站处理器只要开始执行，就不能被截断，强行截断的话，Netty 会抛出异常，如果业务条件不满足的话，
    // 可以不启动出站处理器，大家可以运行示例工程

}
