package com.shengshiyuan.bytebuf;

import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;

public class Test2 {


    public static void main(String[] args) {
        AdaptiveRecvByteBufAllocator allocator = new AdaptiveRecvByteBufAllocator();
        RecvByteBufAllocator.Handle handle = allocator.newHandle();
        System.out.println("==============开始 I/O 读事件模拟==============");
        // 读取循环开始前先重置，将读取的次数和字节数设置为0， 将totalMessages与totalBytesRead设置为0
        handle.reset(null);
        System.out.println(String.format("第一次模拟读，需要分配大小 ：%d", handle.guess()));
        handle.lastBytesRead(512);
        // 调整下次预测值
        handle.readComplete();
        // 在每次读取数据时都需要重置totalMessage 与totalBytesRead
        handle.reset(null);
        System.out.println(String.format("第2次花枝招展读，需要分配大小：%d ", handle.guess()));
        handle.lastBytesRead(512);
        handle.readComplete();

        System.out.println("===============连续2次读取的字节数小于默认分配的字节数= =========================");
        handle.reset(null);
        System.out.println(String.format("第3次模拟读，需要分配大小 ： %d", handle.guess()));



    }
}
