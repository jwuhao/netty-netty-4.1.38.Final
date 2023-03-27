package com.tuling.nio;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ByteBufferAllocateDirect {

    public static void main(String[] args) throws Exception {
        FileInputStream inputStream = new FileInputStream("/Users/quyixiao/git/netty-netty-4.1.38.Final/example/src/input2.txt");
        FileOutputStream outputStream = new FileOutputStream("/Users/quyixiao/git/netty-netty-4.1.38.Final/example/src/output2.txt");

        FileChannel inputChannel = inputStream.getChannel();
        FileChannel outputChannel = outputStream.getChannel();


        ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        while (true) {
            buffer.clear();
            int read = inputChannel.read(buffer);
            System.out.println("read: " + read);
            if (-1 == read) {
                break;
            }
            buffer.flip();
            outputChannel.write(buffer);
        }
        inputChannel.close();
        outputChannel.close();
    }
}
