package io.netty.buffer;

import java.util.Arrays;

public class DirectBufferTest {
    public static void main(String[] args) {
        ByteBuf directBuf = Unpooled.directBuffer(10);
        directBuf.writeBytes("direct buffer".getBytes());
        if (!directBuf.hasArray()) {//检查 ByteBuf 是否由数组支撑。如果不是，则这是一个直接缓冲区
            int length = directBuf.readableBytes();
            byte[] array = new byte[length];//分配一个新的数组来保存具有该长度的字节数据
            directBuf.getBytes(directBuf.readerIndex(), array);//将字节复制到该数组
            System.out.println(Arrays.toString(array));
            System.out.println(length);
        }
    }
}

