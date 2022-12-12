package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;


@ChannelHandler.Sharable
// EchoServerHandler 在前面加了一个特殊的注解 @ChannelHandler.Sharable ， 这个注解的作用是标注一个Handler 实例可以被多个通道安全的共享
// 什么叫作Handler 共享呢？ 就是多个通道的流水线可以加入同一个Handler业务处理器实例， 而这种操作，Netty 默认是不允许的。
// 但是，很多的应用场景需要Handler 业务处理器实例能共享，例如，一个服务器处理十万以上的通道，如果一个通道都新建很多的重复的Handler 实例，
// 就需要十万以上的重复Handler 实例，这就会浪费很多的宝贵的空间，降低了服务器的性能，所以，如果在Handler 实例中，没有与特定的通道强相关的数据或
// 状态 ，建议设计成共享的模式，在前面加一个Netty注解，@ChannelHandler.Sharable
// 反过来，如果没有加@ChannelHandler.Sharable注解，试图将同一个Handler 实例添加到多个ChannelPipeline通道流水线时， Netty将会抛出异常.
// 还有一个隐藏得比较深的重点，同一个通道上的所有业务处理器，只能被 同一个线程处理，所以，不是@ChannelHandler.Sharable 共享的类型的业务处理器
// ,在线程安全层面是安全的，不需要进行线程的同步控制，而不同的通道，可能绑定到多个不同的EventLoop反应器线程，因此，加上了@ChannelHandler.Sharable
// 注解后的共享业务处理器的实例， 可能被多个线程并发执行，这样，就会导致一个结果，@ChannelHandler.Sharable共享实例不是线程层面的安全的。
// 显而易见， @ChannelHandler.Sharable共享的业务处理器，如果需要操作的数据不仅仅是局部变量 ， 则需要进行线程的同步控制， 以保证操作是线程
// 层面的安全。
// 如何判断一个Handler 是否为 @ChannelHandler.Sharable共享呢？ ChannelHandlerAdapter 提供了实用的方法 ， isSharable() , 如果其
// 对应的实现上加上了@ChannelHandler.Sharable注解，那么这个方法将返回true, 表示它可以被添加到多个ChannelPipeline通道的流水线中。
// EchoServerHandler 回显服务器处理器没有保存与任何通道连接相关的数据，也没有内部的其他数据需要保存，所以，它不光可以用来共享，而且不需要做
// 任何的同步控制，在这里为了它加上了 @ChannelHandler.Sharable 注解，表示可以共享，更进一步，这里还设计了一个通用的 Instance 的静态实例，所有
// 的通道直接使用这个Instance 实例即可。
// 最后，提示了一个比较奇怪的问题。

public class EchoServerHandler  extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Neey 版本的EchoServerHandler回显服务器处理器，继承自 ChannelInboundHandlerAdapter,然后覆盖掉channelRead方法 ，这个方法在可读IO
        // 事件到来时，被流水线回调。
        // 1. 这个回显服务器处理器的逻辑分为两步
        // 2. 调用ctx.channel().writeAndFlush()把数据写回到客户端 。
        // 先看第一步，读取从对端输入的数据，channelRead方法的msg参数的形参数类型是不是ByteBuf，而是Object,为什么呢？实际上，msg 的开参数
        // 类型是由流水线的上一站决定的，大家知道，入站的处理的流程是：Netty读取底层的二进制数据，填充到msg时，msg是ByteBuf类型，然后经过
        // 流水线，传入到第一个入站处理器，第一个节点处理完后，将自己的处理结果（类型不一定是ByteBuf作为msg参数），不断的向后传递。
        // 因此msg参数的形参类型，必须是Object类型，不过，可以肯定的是，第二个入站处理器，每一个节点处理完后，将自己的处理结果
        // 不过可以肯定的是，第一个入站处理器的channelRead方法的msg实参类型， 绝对是ByteBuf 类型， 因为它是Netty读取的ByteBuf数据包
         // ,在本实例中， EchoServerHandler 就是第二个业务处理器， 虽然msg的实参类型是Object,蛤实际类型就是ByteBuf ,所以可以强制转换成
        // ByteBuf的类型。
        // 另外，从Netty 4.1 开始，ByteBuf 的默认类型是Direct ByteBuf 直接内存，大家都知道，Java 能直接方法，Direct ByteBuf 内部的数据，必须
        // 先通过getBytes，readBytes等方法，将数据读入到Java数组中，然后才能继续在数据中进行处理
        // 第二步， 将数据写回到客户端，这一步很简单，直接利用前面的msg实例即可，不过要注意，如果上一步使用了readBytes()，那么这一步
        // 就不能直接将msg写回了， 因为数据已经被readBytes读完了，幸好，上一步调用的读数据方法是getBytes， 它不影响ByteBuf的数据指针，
        // 因此可以继续使用，这一步调用了ctx.writeAndFlush，把msg数据写回到客户端，也可以调用ctx.channel().writeAndFlush()方法，将两个方法到这里的效果一
        // 样的，因为这个流水线上没有任何的出站处理器。

        ByteBuf in = (ByteBuf) msg ;
        System.out.println("msg type :" + ((in.hasArray()) ? "堆内存":"直接内存"));
        int len = in.readableBytes();
        byte [] arr = new byte[len];
        in.getBytes(0,arr);
        System.out.println("server received :" + new String(arr, "UTF-8"));
        System.out.println("写回前，msg.refCnt :" + ((ByteBuf) msg).refCnt());
        // 写回数据，异步任务
        ChannelFuture f = ctx.writeAndFlush(msg);
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                System.out.println("写回后，msg.refCnt :" + ((ByteBuf)msg).refCnt());
            }
        });
    }
}
