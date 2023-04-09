package com.shengshiyuan.bytebuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;
import java.util.Iterator;

public class ByteBufTest2 {

    public static void main(String[] args) {
        CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer();

        ByteBuf heapBuf1 = Unpooled.buffer(64).setIndex(0,64);
        ByteBuf heapBuf2 = Unpooled.buffer(64).setIndex(0,64);
        ByteBuf heapBuf3 = Unpooled.buffer(128).setIndex(0,128);
        compositeByteBuf.addComponent(false, 0, heapBuf1);
        compositeByteBuf.addComponent(false, 1, heapBuf2);
        compositeByteBuf.addComponent(false, 1, heapBuf3);

//        compositeByteBuf.removeComponent(0);
        System.out.println(compositeByteBuf.readableBytes());
        byte[] bytes = new byte[70];
        for(int i = 0 ;i < 70 ;i ++){
            bytes[i] = (byte)i;
        }

        compositeByteBuf.setBytes(0, bytes, 0, 70);
        ByteBuf buffer = Unpooled.buffer(128);
        compositeByteBuf.getBytes(0, buffer, 0, 70);

        System.out.println("capacity:" + buffer.capacity());

        if (buffer.hasArray()) {
            byte[] content = buffer.array();
            for (int i = 0; i < 10; i++) {
                System.out.println(content[i]);
            }
        }
    }
}
