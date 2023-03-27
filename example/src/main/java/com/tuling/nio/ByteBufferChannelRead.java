package com.tuling.nio;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ByteBufferChannelRead {


    public static void main(String[] args) {
        try {

            FileInputStream inputStream = new FileInputStream("/Users/quyixiao/git/netty-netty-4.1.38.Final/example/src/input.txt");
            FileOutputStream outputStream = new FileOutputStream("/Users/quyixiao/git/netty-netty-4.1.38.Final/example/src/output.txt");
            FileChannel inputChannel = inputStream.getChannel();
            FileChannel outputChannel = outputStream.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(10);
            while (true) {
                buffer.clear(); // 如果注释掉该行代码会发生什么情况？

                int read = inputChannel.read(buffer);

                System.out.println("read: " + read);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                if (-1 == read) {

                    break;
                }

                buffer.flip();
                outputChannel.write(buffer);
            }
            inputChannel.close();
            outputChannel.close();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
