package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;

public class InHandlerDemoTester {




    public static void main(String[] args) throws Exception {
        final InHandlerDemo inHandlerDemo = new InHandlerDemo();
        ChannelInitializer initializer = new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(inHandlerDemo);
            }
        };
        // 创建嵌入式通道
        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        channel.writeInbound(buf);  // 模拟入站，写入一个入站数据包
        channel.flush();
        // 通道关闭
        channel.close();
        Thread.sleep(Integer.MAX_VALUE);
    }




    // 在讲解上面方法之前，首先对方法进行分类，1.生命周期方法，入站回调方法，上面的几个方法中， channelRead ,channelReadComplete
    // 是处理站处理方法，而其他的6个方法是入站处理器的周期方法
    // 从输出的结果可以看到，ChannelHandler中的回调方法的执行顺序为：handlerAdded()->channelRegistered()->channelActive()->
    // 入站方法回调->channelInactive()->channelUnregistered()->handlerRemoved()，其中，读数据的入站回调为channelRead()->
    // channelReadComplete()，入站方法会多冷色调用，每一次有ByteBuf数据包入站都会调用到。
    // 除了两个入站回调方法外，其余的6个方法都和ChannelHandler和生命周期有关，具体的介绍如下 ：
    // 1. handlerAdded()  :  当业务处理器被加入到流水线后，此方法被回调，也就是完成了ch.pipeline().addLast(handler)语句之后，会回调HandlerAdded();
    // 2. channelRegistered() : 当通道成功绑定一个NioEventLoop线程后，会通过流水线回调所有的业务处理器channelRegistered()方法 。
    // 3. channelActive(): 当通道激活成功后，会通过流水线回调所有的业务处理器，channelActive()方法，通过激活成功指的是，所有的业务处理器
    // 添加，注册的异步任务完成，并且 NioEventLoop线程绑定的异步任务完成 。
    // 4. channelInactive()： 当通道的底层连接已经不是ESTABLISH状态，或者底层连接已经关闭时，会首先回调所有的业务处理的ChannelInactive()方法 。
    // 6. channelUnregistered() : 通道和NioEventLoop线程解除绑定，移除掉对这条通道的事件处理之后，回调所有的业务处理的channelUnregisterd()方法
    // 7.handlerRemoved() : 最后，Netty 会移除掉通道上所有的业务处理器，并且回调所有的业务处理handlerRemoved()方法 。
    // 在上面的6个生命周期方法中， 前面3个在通道创建的时候被先后回调，后面个在通道关闭的时候会先后被加调。
    //
}
