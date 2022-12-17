package io.netty.react;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class OderProtocodDecoder  extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        // RequestMessage message = new RequestMessage();
        // message.decode(bytebuf);
        // out.add(message);
    }


    // 在网络编程中， 我们经常听到各种各自的概念，阻塞，非阻塞
}
