package io.netty.buffer;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class CompositeBufferTest {

    static Charset utf8 = Charset.forName("UTF-8");

    public static void main(String[] args) throws Exception {
        CompositeByteBuf cbuf = ByteBufAllocator.DEFAULT.compositeBuffer();
        // 消息头
        ByteBuf headBuf = Unpooled.copiedBuffer("疯狂创客圈", utf8);
        // 消息体
        ByteBuf bodyBuf = Unpooled.copiedBuffer("高性能Netty", utf8);
        cbuf.addComponents(headBuf, bodyBuf);
        sendMsg(cbuf);
        // 在refCnt为0前面，retain
        headBuf.retain();
        cbuf.release();
        cbuf = ByteBufAllocator.DEFAULT.compositeBuffer();
        // 消息体2
        bodyBuf = Unpooled.copiedBuffer("高性能学习群:", utf8);
        cbuf.addComponents(headBuf, bodyBuf);
        sendMsg(cbuf);
        cbuf.release();
    }


    public static void sendMsg(CompositeByteBuf cbuf) {
        //处理整个消息
        for (ByteBuf b : cbuf) {
            int length = b.readableBytes();
            byte[] array = new byte[length];
            // 将CompositeByteBuf中的数据复制到数组中
            b.getBytes(b.readerIndex(), array);
            // 处理一下数据中的数据
            System.out.println(new String(array, utf8));
        }
        System.out.println();
    }


    public void initCompositeBufComposite() {
        CompositeByteBuf cbuf = Unpooled.compositeBuffer(3);
        cbuf.addComponent(Unpooled.wrappedBuffer(new byte[]{1, 2, 3}));
        cbuf.addComponent(Unpooled.wrappedBuffer(new byte[]{4}));
        cbuf.addComponent(Unpooled.wrappedBuffer(new byte[]{4, 5}));
        // 合并成一个缓冲区
        ByteBuffer nioBuffer = cbuf.nioBuffer(0, 6);
        byte[] bytes = nioBuffer.array();
        System.out.println("bytes = ");
        for (byte b : bytes) {
            System.out.println(b);
        }
        cbuf.release();
    }
}
