package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class StringIntergerHeader extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) throws Exception {
        // 可读字节小于4， 消息头还没有满， 返回
        if (buf.readableBytes() < 4) {
            return;
        }
        // 消息头已经完整
        // 在真正开始缓冲区读取数据之前，调用 markReaderIndex()设置回滚点。
        // 回滚点消息头的readIndex读指针位置

        buf.markReaderIndex();
        int length = buf.readInt();
        // 从缓冲区中读取消息头的大小，这会使得readIndex读指针前移
        // 将剩余长度不够的消息体，重置读指针
        if (buf.readableBytes() < length) {
            // 需要将读指针回滚到旧的读起始位置
            buf.resetReaderIndex();
            return;
        }
        // 读取数据，编码成字符串
        byte[] inBytes = new byte[length];
        buf.readBytes(inBytes, 0, length);
        out.add(new String(inBytes, "UTF-8"));

    }
}
