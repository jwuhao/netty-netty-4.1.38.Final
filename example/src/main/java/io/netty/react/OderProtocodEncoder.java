package io.netty.react;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class OderProtocodEncoder extends MessageToMessageDecoder<ResponseMessage> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ResponseMessage msg, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().buffer();
        // msg.encode(buffer);
        out.add(buf);

    }
}
