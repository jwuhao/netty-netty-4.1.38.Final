package com.tuling.nio;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class MappedByteBuffer {


    public static void main(String[] args) throws Exception {
        RandomAccessFile randomAccessFile = new RandomAccessFile("/Users/quyixiao/git/netty-netty-4.1.38.Final/example/src/NioTest9.txt", "rw");
        // 获取文件通道
        FileChannel fileChannel = randomAccessFile.getChannel();

        java.nio.MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 5);

        mappedByteBuffer.put(0, (byte)'a');
        mappedByteBuffer.put(3, (byte)'b');

        randomAccessFile.close();
    }

}
