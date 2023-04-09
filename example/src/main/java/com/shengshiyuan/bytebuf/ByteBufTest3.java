package com.shengshiyuan.bytebuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

public class ByteBufTest3 {

    public static void main(String[] args) {
        CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer();

        //ByteBuf heapBuf = Unpooled.buffer(10).setIndex(0,10);
        ByteBuf heapBuf = Unpooled.buffer(10);

        compositeByteBuf.addComponent(false,0 ,heapBuf );
//        compositeByteBuf.removeComponent(0);
        System.out.println(compositeByteBuf.readableBytes());
        compositeByteBuf.writeByte(-1);
        for(int i = 0 ;i < 128;i ++){
            compositeByteBuf.writeByte(i);
            if(i == 0 ){
                System.out.println(compositeByteBuf.readerIndex());          // 读索引
                System.out.println(compositeByteBuf.writerIndex());          // 写索引
                System.out.println(compositeByteBuf.capacity());
                System.out.println("===============");
            }
        }

        System.out.println(compositeByteBuf.readerIndex());          // 读索引
        System.out.println(compositeByteBuf.writerIndex());          // 写索引
        System.out.println(compositeByteBuf.capacity());

        for(int i = 0 ;i < 65;i ++){
           compositeByteBuf.readByte();
        }

        compositeByteBuf.writeByte(10);
        compositeByteBuf.writeByte(11);
        compositeByteBuf.writeByte(12);

        compositeByteBuf.readByte();
        compositeByteBuf.writeByte(13);

    }
}
