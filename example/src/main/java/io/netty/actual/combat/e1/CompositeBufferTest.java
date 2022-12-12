package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class CompositeBufferTest {

    static Charset utf8 = Charset.forName("UTF-8");

    public static void main(String[] args) throws Exception {
        CompositeByteBuf cbuf = ByteBufAllocator.DEFAULT.compositeHeapBuffer();
        // 消息头
        ByteBuf headerBuf = Unpooled.copiedBuffer("疯狂创客圈", utf8);

        // 稍息体
        ByteBuf bodyBuf = Unpooled.copiedBuffer("高性能的Netty", utf8);
        cbuf.addComponents(headerBuf, bodyBuf);
        sendMsg(cbuf);
        // 在refCnt为0前，retain
        headerBuf.retain();
        cbuf.release();
        cbuf = ByteBufAllocator.DEFAULT.compositeBuffer();
        // 消息体2
        bodyBuf = Unpooled.copiedBuffer("高性能的学习社群",utf8);
        cbuf.addComponents(headerBuf, bodyBuf);
        sendMsg(cbuf);
        cbuf.release();


    }
    // 在上面的程序中， 向CompositeByteBuf 对象中增加ByteBuf 对象实例，这里调用了addComponents方法，Heap ByteBuf 和Direct ByteBuf
    // 两种类型都可以增加，如果内部只存在一个实例，则CompositeByteBuf 中的hasArray()方法将返回这个唯一的实例hasArray()方法的值，如果有多个
    // 实例，CompositeByteBuf中的hasArray()方法返回false
    // 调用nioBuffer方法可以将CompositeByteBuf实例合并成一个新的Java Nio ByteBuffer(缓冲区），

    public static void sendMsg(CompositeByteBuf buf) {
        // 处理整个消息体
        for (ByteBuf b : buf) {
            int length = b.readableBytes();
            byte[] array = new byte [length];
            // 将CompositeByteBuf 中的数据复制到数组中
            b.getBytes(b.readerIndex(), array);
            // 处理一下数组中的数据
            System.out.println(new String(array, utf8));
        }
        System.out.println();
    }

    public static void initCompositeBufComposite(){
        CompositeByteBuf cbuf = Unpooled.compositeBuffer(3);
        cbuf.addComponent(Unpooled.wrappedBuffer(new byte[]{1,2,3}));
        cbuf.addComponent(Unpooled.wrappedBuffer(new byte[]{4}));
        cbuf.addComponent(Unpooled.wrappedBuffer(new byte[]{5,6}));
        // 合并成一个缓冲区
        ByteBuffer nioBuffer = cbuf.nioBuffer(0,6);
        byte[] bytes = nioBuffer.array();
        for(byte b : bytes){
            System.out.println(b);
        }
        cbuf.release();
    }
    // 在以上的代码中， 使用到了Netty 中一个非常方便的类， Unpooled帮助类， 用它来创建和使用非池化的缓冲区，另外 ， 还可以在Netty 程序
    // 之外独立使用Unpoooled帮助类。

}
