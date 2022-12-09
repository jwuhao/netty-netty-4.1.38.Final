package io.netty.e2;

import io.netty.actual.combat.e1.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

/**
 * create by 尼恩 @ 疯狂创客圈
 *
 * 首先说明一下， 这里将引入了一个新的概念， 入站和出站 ， 简单的来说，入站指的是输出，出站指的是输出 。
 * Netty的Handler 处理器需要处理多种IO事件如可读，可写，对应于不同的IO事件，Netty 提供了一些基础的方法，这些方法都已经提前封装好，后面直接继承或才实现即可。
 * 比如说， 对于处理入站的IO 事件的方法，对应的接口为ChannelInboundHandler入站处理访求channelRead中即可。
 * 在上面例子中channelRead方法，它读取了Netty 的输入数据缓冲区ByteBuf ，Netty的ByteBuf可以对应到前面的介绍Nio的数据缓冲区，它们在功能上
 * 类似的，不过相对而言，Netty 版本性能更好， 使用也更方便，后面会另外一节进行详细的介绍 。
 **/
public class NettyDiscardHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        ByteBuf in = (ByteBuf) msg;
        try {
            Logger.info("收到消息,丢弃如下:");
            while (in.isReadable()) {
                System.out.print((char) in.readByte());
            }
            System.out.println();
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
