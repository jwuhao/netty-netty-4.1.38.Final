package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;


// Netty默认会在ChannelPipeline流水线的最后添加一个TailHandler末尾处理器，实现了默认的处理方法，在这些方法中会帮助完成ByteBuf的内存释放工作
// 在默认情况下，如果每个InboundHandler入站处理器，把最初的ByteBuf数据包一路往下传，那么TailHandler末尾处理器会自动释放掉入站的ByteBuf的实例。
// 如果让ByteBuf数据包通过流水线一路向后传递呢？
//  如果自定义的InboundHandler入站处理器继承自ChannelInBoundHandlerAdapter适配器， 那么可以在InboundHandler的入站处理方法中调用基类的
// 处理方法，示例代码如下
public class DemoHandler  extends ChannelInboundHandlerAdapter {

    /***
     * 出站处理方法
     * @param ctx   上下文
     * @param msg   入站数据包
     * @throws Exception  可能会抛出异常
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg ;
        // ...省略了ByteBuf 的方法，调用父类的入站方法，将msg 向后传递。
        // 总体来说，如果自定义的InboundHandler入站处理器继承自ChannelInboundHandlerAdapter适配器，那么可以调用以下两种方法来翻译ByteBuf 内存
        // 1. 手动释放ByteBuf ，具体的方式为调用byteBuf.release();
        // 2. 调用父类的入站方法，将msg 向后传递，依赖后面的处理器释放ByteBuf，具体的方式为调用蕨类的入站处理方法，super.channelRead(ctx,msg)
        // 释放ByteBuf 的两种方法
        // 方法一， 手动释放ByteBuf
        byteBuf.release();
        // 方法二， 调用父类的入站方法，将msg 向后传递
        super.channelRead(ctx, msg);
    }
}
