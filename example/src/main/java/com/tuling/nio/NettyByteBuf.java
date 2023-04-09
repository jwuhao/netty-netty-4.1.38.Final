package com.tuling.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import javax.naming.ldap.LdapName;

public class NettyByteBuf {



    public static void main(String[] args) {
        // 创建byteBuf 对象，该对象内部包含了一个字节数组byte[10]
        // 通过readerindex和writerIndex和capacity，将buffer分成三个区域
        // 已经读取的区域[0,readerindex]
        // 可读取的区域[readerindex,writerIndex]
        // 可写的区域[writerIndex, capacity)
        ByteBuf byteBuf = Unpooled.buffer(10);
        System.out.println("bytebuf = " + byteBuf);

        for(int i = 0 ;i < 8 ;i ++){
            byteBuf.writeByte(i);
        }

        System.out.println("bytebuf = " + byteBuf);


        for(int i = 0 ;i < 5 ;i ++){
            System.out.println(byteBuf.getByte(i));
        }
        System.out.println("bytebuf = " + byteBuf);

        for(int i = 0 ;i < 5 ;i ++){
            System.out.println(byteBuf.readByte());
        }
        System.out.println("bytebuf = " + byteBuf);

        // 用户 Unpooled工具类创建ByteBuf
        ByteBuf bytebuf2 = Unpooled.copiedBuffer("hello,zhsngsan", CharsetUtil.UTF_8);
        // 使用相关的方法
        if(bytebuf2.hasArray()){
            byte[] content = bytebuf2.array();
            // 将content 转成字符串
            System.out.println(new String(content, CharsetUtil.UTF_8));
            System.out.println("bytebuf = " + bytebuf2);
            System.out.println(bytebuf2.readerIndex());                     // 0
            System.out.println(bytebuf2.writerIndex());                     // 14
            System.out.println(bytebuf2.capacity());                            // 42

            System.out.println(bytebuf2.getByte(0)); // 获取数组0这个位置的字符h 的ascii码， h = 104
            int len = bytebuf2.readableBytes();             // 可读的字节数12
            System.out.println("len=" + len);


            // 使用for 取出各个字节
            for(int i = 0 ;i < len; i++){
                System.out.println((char)bytebuf2.getByte(i));
            }


            // 范围读取
            System.out.println(bytebuf2.getCharSequence(0,6,CharsetUtil.UTF_8));
            System.out.println(bytebuf2.getCharSequence(6,6,CharsetUtil.UTF_8));
        }
    }
}
