package io.netty.react;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class OderProtocodEncoder extends MessageToMessageDecoder<ResponseMessage> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ResponseMessage msg, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().buffer();
        // msg.encode(buffer);
        out.add(buf);

    }
}

// Reactor单线程模式需要显式的构建一个线程数为1的NioEventLoopGroup ,然后传递给ServerBootstrap 使用。
// Reactor 多线程模式使用了默认的构造器构建，NioEventLoopGroup 不是显式的指定线程数为1，因为这和畅 Reactor 模式会根据默认的CPU 内核数对线程数进行起算 ，
// 如今，使用单核CPU 的服务器已经很难看到了， 因此计算出来的线程数肯定大于1 。
// Reactor 主从多线程模式需要显式的声明两个Group ，根据命名习惯，他们分别名boss Group 和worker Group ，boss Group 负责接纳分配工作，其实也就是接收连接
// 并把创建的连接绑定到worker group(NioEventLoopGroup )中的一个worker (NioEventLoop) 上，这个worker 本身用来处理连接上发生的所有事情 。
//



