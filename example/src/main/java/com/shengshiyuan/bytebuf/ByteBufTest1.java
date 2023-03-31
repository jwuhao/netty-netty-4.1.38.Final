package com.shengshiyuan.bytebuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;

public class ByteBufTest1 {

    public static void main(String[] args) {
        ByteBuf byteBuf = Unpooled.copiedBuffer("张hello world", Charset.forName("utf-8"));
        //
        if (byteBuf.hasArray()) {
            byte[] content = byteBuf.array();
            System.out.println(new String(content, Charset.forName("utf-8")));

            System.out.println(byteBuf);

            System.out.println(byteBuf.arrayOffset());          //
            System.out.println(byteBuf.readerIndex());          // 读索引
            System.out.println(byteBuf.writerIndex());          // 写索引
            System.out.println(byteBuf.capacity());             // 最多读取到33这个位置

            int length = byteBuf.readableBytes();               // 返回可读的字节的数量
            System.out.println(length);

            for(int i = 0; i < byteBuf.readableBytes(); ++i) {
                System.out.println((char)byteBuf.getByte(i));
            }
            System.out.println("==============================");
            System.out.println(byteBuf.getCharSequence(0, 4, Charset.forName("utf-8")));
            System.out.println(byteBuf.getCharSequence(4, 6, Charset.forName("utf-8")));
        }
    }
}
