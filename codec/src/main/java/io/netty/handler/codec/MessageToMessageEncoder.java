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
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.TypeParameterMatcher;

import java.util.List;

/**
 * {@link ChannelOutboundHandlerAdapter} which encodes from one message to an other message
 *
 * For example here is an implementation which decodes an {@link Integer} to an {@link String}.
 *
 * <pre>
 *     public class IntegerToStringEncoder extends
 *             {@link MessageToMessageEncoder}&lt;{@link Integer}&gt; {
 *
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} message, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(message.toString());
 *         }
 *     }
 * </pre>
 *
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed through if they
 * are of type {@link ReferenceCounted}. This is needed as the {@link MessageToMessageEncoder} will call
 * {@link ReferenceCounted#release()} on encoded messages.
 *
 *
 * 上一节的示例程序中将POJO对象编码成ByteBuf二进制对象，问题是， 是否能够通过Netty 的编码器将某一种POJO对象编码成另外一种POJO 对象呢？
 * 答案是肯定的，需要继承另外一个Netty 的重要的编码器， MessageToMessageEncoder  编码器，并实现它的encode抽象方法，在子类的encode方法 中
 * 完成原POJ类型的目标POJO类型的编码逻辑，在encode实现方法中，编码完成后，将解码后的目标对象加入到encode方法中的List 实际参数列表中即可。
 * 下面通过实现从字符串String 到整数Integer的编码器，来演示一下MessageToMessageEncoder的使用，此编码器的具体功能是将字符器中所有的数据提出
 * 出来 ， 然后输出到下一站， 代码很简单
 *
 * public class String2IntegerEncoder extends MessageToMessageEncoder<String> {
 *     @Override
 *     protected void encode(ChannelHandlerContext c, String s, List<Object> list) throws Exception {
 *         char[] array = s.toCharArray();
 *         for (char a : array) {
 *             //48 是0的编码，57 是9 的编码
 *             if (a >= 48 && a <= 57) {
 *                 list.add(new Integer(a));
 *             }
 *         }
 *     }
 * }
 *
 *
 * 这里定义了String2IntegerEncoder新类继承自MessageToMessageEncoder基类， 并且明确的入站的数据类型为String，在encode方法中，将字符串的数据
 * 提出出来之后，放入到list列表，其他的字符串直接略过 ， 在子类的encode方法处理完成之后，基类会对这个List 实例的所有元素进行迭代， 将List
 * 列表的元素一个一个的发送给下一站 。
 *
 *
 *  测试字符串到整数编码器
 * public class String2IntegerEncoderTester {
 *
 *
 *     @Test
 *     public void testStringToIntergerDecoder() {
 *         ChannelInitializer i = new ChannelInitializer<EmbeddedChannel>() {
 *             protected void initChannel(EmbeddedChannel ch) {
 *                 ch.pipeline().addLast(new Integer2ByteEncoder());
 *                 ch.pipeline().addLast(new String2IntegerEncoder());
 *             }
 *         };
 *
 *         EmbeddedChannel channel = new EmbeddedChannel(i);
 *
 *         for (int j = 0; j < 100; j++) {
 *             String s = "i am " + j;
 *             channel.write(s);
 *         }
 *         channel.flush();
 *         ByteBuf buf = (ByteBuf) channel.readOutbound();
 *         while (null != buf) {
 *             System.out.println("o = " + buf.readInt());
 *             buf = (ByteBuf) channel.readOutbound();
 *         }
 *         try {
 *             Thread.sleep(Integer.MAX_VALUE);
 *         } catch (InterruptedException e) {
 *             e.printStackTrace();
 *         }
 *
 *     }
 *
 * }
 * 除了需要使用String2IntegerEncoder，这里还需要用到前面的那个编码器Integer2ByteEncoder， 为什么呢？ 因为String2IntegerEncoder
 * 仅仅是编码的第一棒， 负责将字符串编码成整数，Integer2ByteEncoder是编码的第二棒， 将整数进一步的变成ByteBuf 的数据包， 由于出站的
 * 处理过程是从后向前的次数，因此，Integer2ByteEncoder先加入到流水线的前面， String2IntegerEncoder加入流水线的后面。
 *
 * 在实际的开发中， 由于数据的入站和出站的关系紧密， 因此编码器和解码器的关系很紧密，编码和解码更是一种紧密的，相互配套的关系，在流水线处理
 * 时，数据的流动往往是一进一出， 进来进解码，出去时编码，所以在同一个流水线上， 加了某种编码的逻辑，常常需要加一个相对的解码逻辑 。
 *
 * 前面讲到了编码器和解码器都是分开实现的， 例如，通过继承ByteToMessageDecodr基类或者它的子类，完成ByteBuf数据包到POJO 的解码工作，通过继承
 * 基类，MessageToByteEncoder 或者其他子类，完成POJO 到ByteBuf 数据包的编码工作，总之，具有相反的逻辑的编码和解码器，实现两个不同的类中
 * 导致一个结果是， 相互配套的编码器和解码器在加入到通道的流水线时， 常常需要分两次添加 。

 *
 */
public abstract class MessageToMessageEncoder<I> extends ChannelOutboundHandlerAdapter {

    private final TypeParameterMatcher matcher;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     */
    protected MessageToMessageEncoder() {
        matcher = TypeParameterMatcher.find(this, MessageToMessageEncoder.class, "I");
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The type of messages to match and so encode
     */
    protected MessageToMessageEncoder(Class<? extends I> outboundMessageType) {
        matcher = TypeParameterMatcher.get(outboundMessageType);
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelOutboundHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        CodecOutputList out = null;
        try {
            if (acceptOutboundMessage(msg)) {
                out = CodecOutputList.newInstance();
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                try {
                    encode(ctx, cast, out);
                } finally {
                    ReferenceCountUtil.release(cast);
                }

                if (out.isEmpty()) {
                    out.recycle();
                    out = null;

                    throw new EncoderException(
                            StringUtil.simpleClassName(this) + " must produce at least one message.");
                }
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable t) {
            throw new EncoderException(t);
        } finally {
            if (out != null) {
                final int sizeMinusOne = out.size() - 1;
                if (sizeMinusOne == 0) {
                    ctx.write(out.getUnsafe(0), promise);
                } else if (sizeMinusOne > 0) {
                    // Check if we can use a voidPromise for our extra writes to reduce GC-Pressure
                    // See https://github.com/netty/netty/issues/2525
                    if (promise == ctx.voidPromise()) {
                        writeVoidPromise(ctx, out);
                    } else {
                        writePromiseCombiner(ctx, out, promise);
                    }
                }
                out.recycle();
            }
        }
    }

    private static void writeVoidPromise(ChannelHandlerContext ctx, CodecOutputList out) {
        final ChannelPromise voidPromise = ctx.voidPromise();
        for (int i = 0; i < out.size(); i++) {
            ctx.write(out.getUnsafe(i), voidPromise);
        }
    }

    private static void writePromiseCombiner(ChannelHandlerContext ctx, CodecOutputList out, ChannelPromise promise) {
        final PromiseCombiner combiner = new PromiseCombiner(ctx.executor());
        for (int i = 0; i < out.size(); i++) {
            combiner.add(ctx.write(out.getUnsafe(i)));
        }
        combiner.finish(promise);
    }

    /**
     * Encode from one message to an other. This method will be called for each written message that can be handled
     * by this encoder.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to
     * @param msg           the message to encode to an other one
     * @param out           the {@link List} into which the encoded msg should be added
     *                      needs to do some kind of aggregation
     * @throws Exception    is thrown if an error occurs
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, List<Object> out) throws Exception;
}
