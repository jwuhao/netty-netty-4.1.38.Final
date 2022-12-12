package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.Charset;

public class BufferTypeTest {


    final  static  Charset UTF_8 = Charset.forName("UTF-8");
    public static void main(String[] args) {

        ByteBuf heapBuf = ByteBufAllocator.DEFAULT.buffer();
        heapBuf.writeBytes("疯狂创客圈：高恨不能的学习社群".getBytes());
        if(heapBuf.hasArray()){
            // 取得内部数组
            byte [] array = heapBuf.array();
            int offset = heapBuf.arrayOffset() + heapBuf.readerIndex();
            int length = heapBuf.readableBytes();
            System.out.println(new String(array, offset, length,UTF_8));
        }
        heapBuf.release();
    }
}
