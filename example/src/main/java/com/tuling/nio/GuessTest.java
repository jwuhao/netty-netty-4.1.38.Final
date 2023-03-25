package com.tuling.nio;

import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;

public class GuessTest {


    public static void main(String[] args) {
        AdaptiveRecvByteBufAllocator allocator = new AdaptiveRecvByteBufAllocator();
        RecvByteBufAllocator.Handle handle = allocator.newHandle();
        System.out.println("================开始I/O读事件模拟 ============");
        // 读取循环开始前先重置，将读取的次数和字节数设置为0
        // 将totoalMessages 与totalBytesRead置为0
        handle.reset(null);
        System.out.println(String.format("第1次模拟读取，需要分配的大小为%d", handle.guess()));

        handle.lastBytesRead(256);
        // 调整下次预测值
        handle.readComplete();
        // 在每次读取数据时都需要重置totalMessages与totalBytesRead
        handle.reset(null);
        System.out.println(String.format("第2次模拟读，需要分配的内存大小：%d" , handle.guess()));
        handle.lastBytesRead(256);
        handle.readComplete();
        System.out.println("==========连续读取的字节小于默认分配的字节数==========");
        handle.reset(null);
        System.out.println(String.format("第3次模拟读，需要分配的大小为%d",handle.guess()));
        handle.lastBytesRead(512);
        // 调整下次预测值，预测值应该增加到512 *  2 ^ 4
        handle.readComplete();
        System.out.println("============读取的字节变大 =============");
        handle.reset(null);
        // 读循环中缓冲区的的变大
        System.out.println(String.format("第4次模拟读，需要分配的大小: % d",     handle.guess()));
    }
}
