package io.netty.actual.combat.e1;

import io.netty.buffer.*;

public class AllocatorTest {


    public static void main(String[] args) {
        ByteBuf buf = null;
        // 方法1 ，分配默认的初始化容量为9，最大容量为100的缓冲区
        buf = ByteBufAllocator.DEFAULT.buffer(9,100);
        // 方法2，分配默认的分配初始化容量为256，最大容量为Integer.MAX_VALUE的缓冲区。
        buf = ByteBufAllocator.DEFAULT.buffer();
        // 方法三，非池化分配器，分配基于Java 堆Heap 结构内存缓冲区
        buf = UnpooledByteBufAllocator.DEFAULT.heapBuffer();
        // 方法4 ， 池化分本器，分配基于操作系统管理的直接内存缓冲区
        buf = PooledByteBufAllocator.DEFAULT.directBuffer();
        // 其他方法
    }
}
