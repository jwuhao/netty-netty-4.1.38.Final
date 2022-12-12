package io.netty.actual.combat.e1;

/**
 * create by 尼恩 @ 疯狂创客圈
 **/

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;



// StringReplayDecoder类中，每一次decode方法中解码分为两个步骤 。
// 1. 解码出一个字符串的长度
// 2. 按照第一个阶段的字符串长度解码出字符串的内容 。
// 在decode方法中， 每个阶段一完成，就通过checkpoint(Status) 方法把当前的状态设置为新的Status的值 。
// 为了处理StringReplayDecoder解码后的字符串， 这里编写了一个简单的业务处理器，其功能是读取上一站的入站数据，把它转换成字符串，并且输出到
// Console控制台上， 新业务处理器名称为StringProcessHandler，具体如下

public class StringProcessHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String s = (String) msg;
        System.out.println("打印: " + s);
    }
}