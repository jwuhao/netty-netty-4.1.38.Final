package io.netty.actual.combat.e1;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;

// Pipe line 流水线
// 前面讲到过， 一条Netty通道需要很我铁Handler业务处理器来处理业务，每条通道内部都有一条流水线Pipeline,将Handler 封装起来，
// Netty 的业务处理器流水线 ChannelPipeline是基于责任链设计模式 （Chain of Reposibility）来设计的，内部是一个双向链表结构
// 能支持动态的添加和删除Handler 处理器，首先看一下流水线的处理流程。
public class InPipeline {



    static  class SimpleInhandlerA extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("入站处理器， A : 被回调");
            super.channelRead(ctx, msg);
        }


    }


    static  class SimpleHandleB extends ChannelInboundHandlerAdapter{
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("入站处理器 B: 被回调");
            // 不调用基类的channelRead,终止流水线的执行
       //      super.channelRead(ctx, msg);
        }
    }


    static class SimpleHandleC extends  ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("入站处理器C :  被 回调");
            super.channelRead(ctx, msg);
        }
    }


    public static void main(String[] args) {
        ChannelInitializer initializer = new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new SimpleInhandlerA());
                ch.pipeline().addLast(new SimpleHandleB());
                ch.pipeline().addLast(new SimpleHandleC());
            }
        };


        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        channel.writeInbound(buf); // 向通道写入一个入站报文 ，数据包

        try {
            Thread.sleep(Integer.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }



    // 在channelRead()方法中， 我们打印当前Handler 业务处理器的信息，然后调用父类的channelRead()方法，而父类的channelRead()方法会自动
    // 调用inBoundHandler的channelRead()方法，并且会把当前的inBoundHandler入站处理器中处理完毕的对象传递到下一个inBoundHandler入站
    // 处理器，我们示例程序中传递的对象都是同一个信息(msg)
    // 在channelRead() 方法中，如果不调用父类的channelRead()方法，结果会如何呢？ 大家可以自行尝试
    // 运行实践安全的代码如下 ，输出的结果如下
    // 入站处理器A : 被回调
    // 入站入理器B :被回调
    // 入站处理器C : 被回调
    // 我们可以看到，入站处理器的流动次数是，从前向后的，加在前面执行的也是前面。具体如下图所示 6-11 所示







}
