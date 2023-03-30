package com.tuling.nio;

import java.nio.ByteBuffer;

public class ByteBufferCompact {


    public static void main(String[] args) {


        ByteBuffer buffer = ByteBuffer.allocate(10);

        System.out.println("capacity: " + buffer.capacity());


        for(int i = 0; i < 10; ++i) {
            buffer.put((byte)i);
        }

        System.out.println("before flip limit: " + buffer.limit());

        buffer.flip();

        System.out.println(buffer.get());
        System.out.println(buffer.get());

        System.out.println("==========================");
        System.out.println("limit = " + buffer.limit());
        System.out.println("position = " + buffer.position());
        System.out.println("capacity = " + buffer.capacity());
        System.out.println("=======================");


        ByteBuffer byteBuffer = buffer.compact();


        System.out.println("==========================");
        System.out.println("limit = " + byteBuffer.limit());
        System.out.println("position = " + byteBuffer.position());
        System.out.println("capacity = " + byteBuffer.capacity());
        System.out.println("=======================");

        while (byteBuffer.hasRemaining()){
            System.out.println(byteBuffer.get());
        }



    }
}
