package com.tuling.nio;

import io.netty.actual.combat.e1.BufferTypeTest;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;

public class GuessTest {


    public static void main(String[] args) {
        AdaptiveRecvByteBufAllocator allocator = new AdaptiveRecvByteBufAllocator();
        // 获取计算内存分配器Handle
        RecvByteBufAllocator.Handle handle = allocator.newHandle();
        System.out.println("================开始I/O读事件模拟 ============");
        // 读取循环开始前先重置，将读取的次数和字节数设置为0
        // 将totalMessages 与totalBytesRead置为0
        handle.reset(null);
        int guess0 =  handle.guess();
        System.out.println(String.format("第1次模拟读取，需要分配的大小为%d", guess0));
        // 设置尝试读取字节数组的buf的可写字节数
       // handle.attemptedBytesRead(256);
        handle.lastBytesRead(256);
        // 调整下次预测值
        handle.readComplete();
        // 在每次读取数据时都需要重置totalMessages与totalBytesRead
        //handle.reset(null);
        int guess = handle.guess();
        System.out.println(String.format("第2次模拟读，需要分配的内存大小：%d", guess));
        handle.lastBytesRead(256);
        handle.readComplete();
        System.out.println("==========连续读取的字节小于默认分配的字节数==========");
       // handle.reset(null);
        int guess1 = handle.guess();
        System.out.println(String.format("第3次模拟读，需要分配的大小为%d",guess1));
        handle.lastBytesRead(512);
        // 调整下次预测值，预测值应该增加到512 *  2 ^ 4
        handle.readComplete();
        System.out.println("============读取的字节变大 =============");
        handle.reset(null);
        int  guess2 =  handle.guess();
        // 读循环中缓冲区的的变大
        System.out.println(String.format("第4次模拟读，需要分配的大小: % d",   guess2 ));
    }
}
