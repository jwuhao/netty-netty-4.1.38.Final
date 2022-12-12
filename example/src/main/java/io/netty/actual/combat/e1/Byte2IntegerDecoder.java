package io.netty.actual.combat.e1;

/**
 * create by 尼恩 @ 疯狂创客圈
 **/

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;


// 整数解码器
// 1. 定义一个新的整数解码器， Byte2IntegerDecoder 类， 让这个类继承Netty 的 ByteToMessageDecoder 字节码解码抽象类
// 2. 实现父类的decode 方法，将ByteBuf 缓冲区的数据，解码成一个一个的Integer对象
// 3. 在decode方法中，将解码后得到的Integer整数，加入到父类传递过来的List<Object>实参中。

public class Byte2IntegerDecoder extends ByteToMessageDecoder {
    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in,
                       List<Object> out) {
        while (in.readableBytes() >= 4) {
            int i = in.readInt();
            Logger.info("解码出一个整数: " + i);
            out.add(i);
        }
    }
}