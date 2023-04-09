package com.tuling.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

public class NettyByteBuf2 {



    public static void main(String[] args) {
        ByteBuf byteBuf = Unpooled.buffer(10);
        System.out.println("bytebuf = " + byteBuf);

        for (int i = 0; i < 8; i++) {
            byteBuf.writeByte(i);
            if (i == 7 ) {
                byteBuf.markWriterIndex();
            }
        }



        for (int i = 0; i < 5; i++) {
            System.out.println(byteBuf.readByte());
            if( i == 2 ){
                byteBuf.markReaderIndex();
            }
        }
        System.out.println("readerIndex = " + byteBuf.readerIndex());
        System.out.println("writerIndex="+byteBuf.writerIndex());

        byteBuf.discardReadBytes();
        System.out.println("bytebuf = " + byteBuf);

        System.out.println("readerIndex = " + byteBuf.readerIndex());
        System.out.println("writerIndex="+byteBuf.writerIndex());
    }
}
