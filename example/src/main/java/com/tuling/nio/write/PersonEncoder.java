package com.tuling.nio.write;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class PersonEncoder extends MessageToByteEncoder<Person> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Person msg, ByteBuf out) throws Exception {
        if (msg instanceof Person) {
            out.writeBytes(JSON.toJSONString(msg).getBytes("utf-8"));
        }
    }
}
