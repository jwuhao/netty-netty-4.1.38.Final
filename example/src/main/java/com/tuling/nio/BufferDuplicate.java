package com.tuling.nio;

import java.nio.ByteBuffer;

public class BufferDuplicate {

    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        System.out.println("-----------------" + buffer.capacity());

        for (int i = 0; i < buffer.capacity(); ++i) {
            buffer.put((byte) i);
        }
        ByteBuffer newBuffer = buffer.duplicate();

        System.out.println("原来旧对象打印");

        buffer.flip();
        newBuffer.flip();
        for(int i = 0 ;i < 3 ;i ++){
            System.out.println(buffer.get());
            if(i == 2 ){
                buffer.put(2,(byte)(buffer.get() * 3 ));
            }
        }

        System.out.println("============复制对象打印===========");
        for(int j = 0 ;j < 3 ;j ++){
            System.out.println(newBuffer.get());
        }

    }
}
