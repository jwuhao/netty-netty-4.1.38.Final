package com.tuling.nio;

import sun.security.util.AuthResources_it;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class IntBufferTest {


    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(10);

        System.out.println("capacity: " + buffer.capacity());


        for (int i = 0; i < 5; ++i) {
            buffer.put((byte)i);
        }

        System.out.println("before flip limit: " + buffer.limit());

        buffer.flip();

        System.out.println("after flip limit: " + buffer.limit());

        System.out.println("enter while loop");

        for(int i = 0 ;i < 10 ;i ++){
            System.out.println("position: " + buffer.position());
            System.out.println("limit: " + buffer.limit());
            System.out.println("capacity: " + buffer.capacity());

            System.out.println(buffer.get());
            System.out.println("=======================================");
        }
    }
}
