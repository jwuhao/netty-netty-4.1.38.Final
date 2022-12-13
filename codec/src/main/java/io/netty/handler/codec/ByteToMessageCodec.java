/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.TypeParameterMatcher;

import java.util.List;

/**
 * A Codec for on-the-fly encoding/decoding of bytes to messages and vise-versa.
 *
 * This can be thought of as a combination of {@link ByteToMessageDecoder} and {@link MessageToByteEncoder}.
 *
 * Be aware that sub-classes of {@link ByteToMessageCodec} <strong>MUST NOT</strong>
 * annotated with {@link @Sharable}.
 *
 *
 * 现在的问题是，具有相互配套的逻辑的编码器和解码器能否放在同一个类中呢？ 答案是肯定的，就是要用到Netty 新类型，Codec类型。
 *  完成POJO 到  ByteBuf数据包的配套的编码器和解码器的基类， 叫作 ByteToMessageCodec<I> 它是一个抽象类，从功能上来说，继承它
 *  就等同于继承了ByteToMessageDecoder 解码器和MessageToByteEncoder编码器这两个基类 。
 *
 *  ByteToMessageCodec 同时包含了编码和解码encode和解码decode两个抽象方法 ， 这两个方法需要我们自己实现。
 *  下面是一整数的字节，字节到整数的编解码器。
 *  public class Byte2IntegerCodec extends ByteToMessageCodec<Integer> {
 *     @Override
 *     public void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out)
 *             throws Exception {
 *         out.writeInt(msg);
 *         System.out.println("write Integer = " + msg);
 *     }
 *
 *
 *     @Override
 *     public void decode(ChannelHandlerContext ctx, ByteBuf in,
 *                        List<Object> out) throws Exception {
 *         if (in.readableBytes() >= 4) {
 *             int i = in.readInt();
 *             System.out.println("Decoder i= " + i);
 *             out.add(i);
 *         }
 *
 *     }
 * }
 * 这是编码器和解码器的结合，简单的通过继承的方法，将前面的编码器的encode方法和解码器的decode方法放在了同一个自定义的类中，这样的逻辑上更加
 * 紧密， 当然在使用时，加入到流水线中， 也只需要加入一次。
 * 从上面的示例程序上来看，ByteToMessageCodec 编解码器和前面的编码器与解码器分开来实现相比，仅仅少写了一个类，少加入了一次流水线，仅此而已 。
 * 在技术上和功能上都没有增加太多的难度 。
 * 对于 POJO 之间进行转换的编码和解码，Netty 将 MessageToMessageEncoder 编码器和MessageToMessageDecoder解码器进行了简单的整合，整合出了一个
 * 新的基类，MessageToMessageCodec 编解码器，这个基类同时包含了编码encode和解码decode两个抽象方法，用于完成pojo-to-pojo的双向转换，
 * 这仅仅是使用形式变得简化，在技术上并没有增加太多的难度。
 *
 * 半包现象的原理
 * 寻根粘包和装饰的来源得从操作系统底层说起。
 * 大家都知道，底层网络是以二进制字节报文的形式来传输数据的， 读数据的过程大致为，当IO 可读时，Netty 会从底层网络将二进制数据读到ByteBuf
 * 缓冲区中，再次给Netty程序转成Java POJO对象 。
 * 在发送Netty应用层进程缓冲区，程序以ByteBuf 为单位来发送数据，但是为了底层的操作系统内核缓冲区，底层会按照fyyr规范对数据包进行二次拼装 ，
 * 拼装成传输层TCP层协议报文，再进行发送，在接收端收到传输层的二进制包后，首先保存在内核缓冲区， Netty读取ByteBuf 时才复制到进程缓冲区。
 *
 * 在接收端，当Netty 程序将数据从内核缓冲区复制到Netty进程缓冲区的ByteBuf 时，问题就来了。
 * 1. 首先，每次读取底层的缓冲的数据容量是有限制的，当TCP 底层缓冲的数据包比较大时，会将一个底层包分成多次ByteBuf进行复制，进而千万进程缓冲区读到的包是半包。
 * 2. 当TCP 底层缓冲区的数据包比较小时， 一次复制去不止一个内核缓冲区包， 进而千万进程缓冲区读到的包是粘包。
 *
 * 如何解决呢？
 * 基本思路是，在接收端，Netty 程序需要根据自定义协议，将读取的进程缓冲区ByteBuf 在应用层进行二次拼装 ， 重新组装我们的应用层的数据包， 接收端
 * 的这个过程通常也称为分包，或者叫作拆包。
 *
 * 1. 可以自定义解码器分包器：基于ByteToMessageDecorder或者ReplayingDecoder，定义自己的进程缓冲区分包器。
 * 2. 使用NEtty内置的解码器，如使用Netty 的内置的LengthFieldBasedFrameDecoder自定义分隔符数据包解码器， 对进程缓冲区ByteBuf进行正确的分包。
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
public abstract class ByteToMessageCodec<I> extends ChannelDuplexHandler {

    private final TypeParameterMatcher outboundMsgMatcher;
    private final MessageToByteEncoder<I> encoder;

    private final ByteToMessageDecoder decoder = new ByteToMessageDecoder() {
        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            ByteToMessageCodec.this.decode(ctx, in, out);
        }

        @Override
        protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            ByteToMessageCodec.this.decodeLast(ctx, in, out);
        }
    };

    /**
     * see {@link #ByteToMessageCodec(boolean)} with {@code true} as boolean parameter.
     */
    protected ByteToMessageCodec() {
        this(true);
    }

    /**
     * see {@link #ByteToMessageCodec(Class, boolean)} with {@code true} as boolean value.
     */
    protected ByteToMessageCodec(Class<? extends I> outboundMessageType) {
        this(outboundMessageType, true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     */
    protected ByteToMessageCodec(boolean preferDirect) {
        ensureNotSharable();
        outboundMsgMatcher = TypeParameterMatcher.find(this, ByteToMessageCodec.class, "I");
        encoder = new Encoder(preferDirect);
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The type of messages to match
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     */
    protected ByteToMessageCodec(Class<? extends I> outboundMessageType, boolean preferDirect) {
        ensureNotSharable();
        outboundMsgMatcher = TypeParameterMatcher.get(outboundMessageType);
        encoder = new Encoder(preferDirect);
    }

    /**
     * Returns {@code true} if and only if the specified message can be encoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return outboundMsgMatcher.match(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        decoder.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        encoder.write(ctx, msg, promise);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        decoder.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        decoder.channelInactive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        try {
            decoder.handlerAdded(ctx);
        } finally {
            encoder.handlerAdded(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            decoder.handlerRemoved(ctx);
        } finally {
            encoder.handlerRemoved(ctx);
        }
    }

    /**
     * @see MessageToByteEncoder#encode(ChannelHandlerContext, Object, ByteBuf)
     * 编码方法（encode ）
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception;

    /**
     * @see ByteToMessageDecoder#decode(ChannelHandlerContext, ByteBuf, List)
     * 解码方法
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;

    /**
     * @see ByteToMessageDecoder#decodeLast(ChannelHandlerContext, ByteBuf, List)
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.isReadable()) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decode(ctx, in, out);
        }
    }

    private final class Encoder extends MessageToByteEncoder<I> {
        Encoder(boolean preferDirect) {
            super(preferDirect);
        }

        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return ByteToMessageCodec.this.acceptOutboundMessage(msg);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception {
            ByteToMessageCodec.this.encode(ctx, msg, out);
        }
    }
}
