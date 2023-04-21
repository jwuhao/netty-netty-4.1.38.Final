package com.tuling.nio.write;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class UserEncoder extends MessageToByteEncoder<User> {

    @Override
    protected void encode(ChannelHandlerContext ctx, User msg, ByteBuf out) throws Exception {
        if (msg instanceof User) {
            out.writeBytes(JSON.toJSONString(msg).getBytes("utf-8"));
        }
    }
}
