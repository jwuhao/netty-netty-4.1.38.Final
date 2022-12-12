package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.Charset;

public class DirectBufferTest {


    final static Charset UTF_8 = Charset.forName("UTF-8");

    public static void main(String[] args) {
        ByteBuf directBuf = ByteBufAllocator.DEFAULT.directBuffer();
        directBuf.writeBytes("疯狂创客圈：高性能学习社群 ".getBytes());
        // 注意，如果hasArray()返回的是false , 不一定代表缓冲区一定就是Direct ByteBuf 直接缓冲区， 也可能是CompositeByteBuf 缓冲区
        // 很多的通信编程场景下，需要多个  ByteBuf 组成一个完整的消息，例如 HTTP 协议传输时的消息总是由Header(消息头)和Body(消息体)
        // 组成的，如果传输的内容很长，就会分成多个消息包进行发送，消息中的Header就需要重用，而不是每次发送都创建新的Header
        if (!directBuf.hasArray()) {
            int length = directBuf.readableBytes();
            byte[] array = new byte[length];
            // 把数据读取到堆内存
            directBuf.getBytes(directBuf.readerIndex(), array);
            System.out.println(new String(array, UTF_8));
        }

        directBuf.release();
        // 注意，如果hasArray()返回false, 不一定代表缓冲区一定就是Direct ByteBuf 直接缓冲区，也有可能是CompositeByteBuf 缓冲区
        // 在很多的通信编程场景下， 需要多个ByteBuf 组成一个完整的消息，例如 HTTP协议传输消息总是由Header(消息头) 和 Body(消息体)
        // 组成的， 如果传输的内容很长， 就会分成多个消息包进行发送， 消息中的Header 就需要重用，而不是每次发送都创建新的Header
        //


    }
}
