package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;

/**
 * create by 尼恩 @ 疯狂创客圈
 **/
public class IntegerAddDecoder
        extends ReplayingDecoder<IntegerAddDecoder.Status> {

    enum Status {
        PARSE_1, PARSE_2
    }

    private int first;
    private int second;

    public IntegerAddDecoder() {
        //构造函数中，需要初始化父类的state 属性，表示当前阶段
        super(Status.PARSE_1);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in,
                          List<Object> out) throws Exception {


        switch (state()) {
            case PARSE_1:                       // 第一个阶段，读取前面的整数
                //从装饰器ByteBuf 中读取数据
                first = in.readInt();
                //第一步解析成功，
                // 进入第二步，并且设置“读指针断点”为当前的读取位置
                checkpoint(Status.PARSE_2);
                break;
            case PARSE_2:       // 第二个阶段，读取后事的整数，然后相加
                second = in.readInt();
                Integer sum = first + second;
                out.add(sum);
                checkpoint(Status.PARSE_1);
                break;
            default:
                break;
        }

        // 第一个阶段完成，就通过checkpoint(Status)方法，把当前的状态设置为新的Status的值，这个值保存在ReplayingDecoder 的state属性中
        // 严格来说，checkpoint（Status）方法有两个作用
        // 设置state属性值，更新一下当前的状态
        // 2. 还有一个非常大的作用，就是设置读断点指针
        // 什么是ReplyingDecoder的读断点指针呢？
        // 读断点指针，是ReplyingDecoder类的另外一个重要的成员， 它保存在装饰器的内部ReplayingDecoderBuffer成员的起始读指针，有点
        // 类似于mark标记，当计数据时， 一时可读数据不够，ReplayingDecoderBuffer在抛出ReplayError异常之前，ReplayingDecoder会把
        // 读指针的值还原到之前的checkpoint(IntegerAddDecoder.Status)方法设置的读断点指针（checkpoint） ，于是，ReplayingDecoder
        // 在下一次读取时，还会从之前设置的断点位置开始。
        // checkpoint(IntegerAddDecoder.Status )方法，仅仅从参数上看上去比较奇怪，参数为要设置阶段，但是它的功能却又包含了读断点指针的设置
        // 总结一下，在这个IntegerAddDecoder 的使用实例中， 解码器保持以下状态信息。
        // 1. 当前通道的读取阶段，是Status.PARSE_1或者Status.PARSE_2 。
        // 2. 每一次读取，还要保持当前读断点指针，便于在可读数据不足时进行恢复
        // 因此，IntegerAddDecoder是有状态的， 不能在不同的通道之间进行共享，更加进一步来说，ReplayingDecoder类型和其他所有的子类都需要保存状态信息
        // 都是有状态的，都不适合在不同的通道之间共享 。
        // 至此，IntegerAddDecoder已经介绍完了。
        // 最后，如何使用上面的InterAddDecorder解码器呢？
        // 具体的测试实例和前面的Byte2IntegerDecoder使用的实例大致相同，由于篇幅的限制，这里就不再赘述 。


    }
}