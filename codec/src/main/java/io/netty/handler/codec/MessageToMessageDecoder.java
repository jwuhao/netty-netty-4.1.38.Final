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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.TypeParameterMatcher;

import java.util.List;

/**
 * {@link ChannelInboundHandlerAdapter} which decodes from one message to an other message.
 *
 *
 * For example here is an implementation which decodes a {@link String} to an {@link Integer} which represent
 * the length of the {@link String}.
 *
 * <pre>
 *     public class StringToIntegerDecoder extends
 *             {@link MessageToMessageDecoder}&lt;{@link String}&gt; {
 *
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link String} message,
 *                            List&lt;Object&gt; out) throws {@link Exception} {
 *             out.add(message.length());
 *         }
 *     }
 * </pre>
 *
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed through if they
 * are of type {@link ReferenceCounted}. This is needed as the {@link MessageToMessageDecoder} will call
 * {@link ReferenceCounted#release()} on decoded messages.
 *
 *
 * 前面的解码器都是将ByteBuf 缓冲区中的二进制数据解码成Java 的普通POJO对象，有一个问题，是否存在一些解码器， 将一种POJO 对象解码成另外一种
 * POJO对象呢？答案是：存在的。
 * 与前面的不同的是，在这种应用场景下Decoder 解码器，需要继承一个新的Netty 解码器基类， MessageToMessageDecoder<I>  ，在继承它的时候
 * 需要明确的泛型实参<I> ， 这个实参的作用就是指定入站的消息Java POJO类型。
 * 这里有个问题：为什么 MessageToMessageDecoder<I> 需要指定入站数据类型，而前面使用的 ByteToMessageDecoder解码的ByteBuf 的时候
 * 不需要指定泛型实参，原因很简单，ByteToMessageDecoder的入站消息类型是十分明确的， 就是二进制缓冲区的ByteBuf 类型，但MessageTomessageDecoder
 * 的入站消息类型不是很明确，可以在任何的POJO类型，所以需要指定 。
 *
 * MessageToMessageDecoder 类也是一个入站的处理器， 也有一个decode抽象方法，decoder具体解码的逻辑需要子类去实现。
 * 下面通过实现一个整体的Integer到字符串String的解码器，演示了一个 MessageToMessageDecoder的使用，此解码器具体功能
 * 是将整数转换成字符串中，然后输出到下一站
 * Integer2StringDecoder
 *
 *
 *
 * Encoder 原理与实践
 * 在Netty 的业务处理完后，业务处理的结果往往是某个Java POJO对象，需要编码成最终的ByteBuf二进制类型，通过流水线写入到底层的Java通道 。
 * 在Netty中，什么叫编码器呢？ 首先编码器是一个Outbound出站处理器，负责处理出站的数据，其次，编码器将上一站Outbound出让处理器传过来的输入
 * Input数据进行编码或者格式转换，然后传递到下一站ChannelOutBoundHandler出站处理器。
 * 编码器与解码器相呼应，Netty 中的编码器负责将出站的某种Java POJO对象编码成二提制的ByteBuf ， 或者 编码成另一我种Java POJO对象 。
 * 编码器ChannelOutboundHandler出站处理器的实现类， 一个编码器将出站对象编码之后，编码后的数据将被传递到下一个ChannelOutBoundHandler出站
 * 处理对象，进行后面的出站处理。
 * 由于最后只有ByteBuf才能写入到通道中去，因此，可以肯定通道流水线上装配的第一个编码器一定是把数据编码成ByteBuf类型，这个问题，为什么编码成ByteBuf
 * 类型的数据包编码器是在流水线的头部， 而不是流水线的尾部呢？原因很简单，因为出让处理的顺序是从后向前的。
 *
 * MessageToMessageDecoder 是一个非常重要的编码基类， 它位于 Netty 的io.netty.handler.codec包中， MessageToByteEncoder的功能是将一个
 * Java  POJO 对象编码成一个ByteBuf数据包， 它是一个抽象类，仅实现了编码的基础流程， 在编码过程中，通过调用encode抽象方法来完成，但是它的encode
 * 编码方法是一个抽象方法，没有具体的encode编码逻辑实现， 而实现encode抽象方法的工作需要子类去完成 。
 *
 * 如果要实现一个自己的编码器， 则需要继承自MessageToByteEncoder基类， 实现它的encode抽象方法，作为演示，下面实现一个整数编码器， 其功能是将
 * java 整数编码成二进制ByteBuf 数据包，这个示例代码如下 。
 *
 *public class Integer2ByteEncoder extends MessageToByteEncoder<Integer> {
 *     @Override
 *     public void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out)
 *             throws Exception {
 *         out.writeInt(msg);
 *         Logger.info("encoder Integer = " + msg);
 *     }
 * }
 *
 * 继承MessageToMessageDecoder时，需要带上实参，表示编码之前的Java POJO原类型，在这个示例程序中， 编码之前的类型是Java 整数类型(Integer)
 * 上面的encode方法实现很简单，将入站的数据对象msg农牧民入到Out实参即可，基类传入的ByteBuf类型的对象，编码完成后，基类MessageToByteEncoder
 * 会将输出的ByteBuf数据包发送到下一站 。
 *
 *
 *
 */
public abstract class MessageToMessageDecoder<I> extends ChannelInboundHandlerAdapter {

    private final TypeParameterMatcher matcher;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     */
    protected MessageToMessageDecoder() {
        matcher = TypeParameterMatcher.find(this, MessageToMessageDecoder.class, "I");
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType    The type of messages to match and so decode
     */
    protected MessageToMessageDecoder(Class<? extends I> inboundMessageType) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            if (acceptInboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                try {
                    decode(ctx, cast, out);
                } finally {
                    ReferenceCountUtil.release(cast);
                }
            } else {
                out.add(msg);
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            int size = out.size();
            for (int i = 0; i < size; i ++) {
                ctx.fireChannelRead(out.getUnsafe(i));
            }
            out.recycle();
        }
    }

    /**
     * Decode from one message to an other. This method will be called for each written message that can be handled
     * by this decoder.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param msg           the message to decode to an other one
     * @param out           the {@link List} to which decoded messages should be added
     * @throws Exception    is thrown if an error occurs
     */
    protected abstract void decode(ChannelHandlerContext ctx, I msg, List<Object> out) throws Exception;
}
