package com.tuling.nio.example3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;



// 自定义Handler需要继承netty 规定好的某个HandlerAdapter(规范)
public class NettyServerHandler2 extends LengthFieldBasedFrameDecoder {


    public NettyServerHandler2(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength);
    }


    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        //在这里调用父类的方法,实现指得到想要的部分,我在这里全部都要,也可以只要body部分
        in = (ByteBuf) super.decode(ctx,in);

        if(in == null){
            return null;
        }
        if(in.readableBytes()<4){
            throw new Exception("字节数不足");
        }

        //读取length字段
        int length = in.readInt();

        if(in.readableBytes()!=length){
            throw new Exception("标记的长度不符合实际长度");
        }
        //读取body
        byte []bytes = new byte[length];
        in.readBytes(bytes);

        return new String(bytes,"UTF-8");

    }


}
