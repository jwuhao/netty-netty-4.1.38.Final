package com.tuling.nio.base;

import io.netty.buffer.AbstractByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.Arrays;
import java.util.List;


// 自定义Handler需要继承netty 规定好的某个HandlerAdapter(规范)
public class NettyServerHandler2 extends ByteToMessageDecoder {

    private int alreadyReadLength ;

    private int sumByteLength ;


    private  ByteBuf buf ;


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        System.out.println("NettyServerHandler2 invoke");
        if (sumByteLength == 0) {
            sumByteLength = in.readInt();
            buf = Unpooled.copiedBuffer("", CharsetUtil.UTF_8);
        }
        int readableBytes = in.readableBytes();
        alreadyReadLength += readableBytes;

        byte[] data = new byte[readableBytes];
        in.readBytes(data);

        buf.writeBytes(data);
        if (alreadyReadLength == sumByteLength) {
            sumByteLength = 0;
            byte[] outData = new byte[buf.readableBytes()];
            buf.readBytes(outData);

            out.add(new String(outData,"utf-8"));
            buf.release();
            buf = null;
        }
    }
}
