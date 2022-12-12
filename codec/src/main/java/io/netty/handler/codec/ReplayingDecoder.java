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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.Signal;
import io.netty.util.internal.StringUtil;

import java.util.List;

/**
 * A specialized variation of {@link ByteToMessageDecoder} which enables implementation
 * of a non-blocking decoder in the blocking I/O paradigm.
 * <p>
 * The biggest difference between {@link ReplayingDecoder} and
 * {@link ByteToMessageDecoder} is that {@link ReplayingDecoder} allows you to
 * implement the {@code decode()} and {@code decodeLast()} methods just like
 * all required bytes were received already, rather than checking the
 * availability of the required bytes.  For example, the following
 * {@link ByteToMessageDecoder} implementation:
 * <pre>
 * public class IntegerHeaderFrameDecoder extends {@link ByteToMessageDecoder} {
 *
 *   {@code @Override}
 *   protected void decode({@link ChannelHandlerContext} ctx,
 *                           {@link ByteBuf} buf, List&lt;Object&gt; out) throws Exception {
 *
 *     if (buf.readableBytes() &lt; 4) {
 *        return;
 *     }
 *
 *     buf.markReaderIndex();
 *     int length = buf.readInt();
 *
 *     if (buf.readableBytes() &lt; length) {
 *        buf.resetReaderIndex();
 *        return;
 *     }
 *
 *     out.add(buf.readBytes(length));
 *   }
 * }
 * </pre>
 * is simplified like the following with {@link ReplayingDecoder}:
 * <pre>
 * public class IntegerHeaderFrameDecoder
 *      extends {@link ReplayingDecoder}&lt;{@link Void}&gt; {
 *
 *   protected void decode({@link ChannelHandlerContext} ctx,
 *                           {@link ByteBuf} buf) throws Exception {
 *
 *     out.add(buf.readBytes(buf.readInt()));
 *   }
 * }
 * </pre>
 *
 * <h3>How does this work?</h3>
 * <p>
 * {@link ReplayingDecoder} passes a specialized {@link ByteBuf}
 * implementation which throws an {@link Error} of certain type when there's not
 * enough data in the buffer.  In the {@code IntegerHeaderFrameDecoder} above,
 * you just assumed that there will be 4 or more bytes in the buffer when
 * you call {@code buf.readInt()}.  If there's really 4 bytes in the buffer,
 * it will return the integer header as you expected.  Otherwise, the
 * {@link Error} will be raised and the control will be returned to
 * {@link ReplayingDecoder}.  If {@link ReplayingDecoder} catches the
 * {@link Error}, then it will rewind the {@code readerIndex} of the buffer
 * back to the 'initial' position (i.e. the beginning of the buffer) and call
 * the {@code decode(..)} method again when more data is received into the
 * buffer.
 * <p>
 * Please note that {@link ReplayingDecoder} always throws the same cached
 * {@link Error} instance to avoid the overhead of creating a new {@link Error}
 * and filling its stack trace for every throw.
 *
 * <h3>Limitations</h3>
 * <p>
 * At the cost of the simplicity, {@link ReplayingDecoder} enforces you a few
 * limitations:
 * <ul>
 * <li>Some buffer operations are prohibited.</li>
 * <li>Performance can be worse if the network is slow and the message
 *     format is complicated unlike the example above.  In this case, your
 *     decoder might have to decode the same part of the message over and over
 *     again.</li>
 * <li>You must keep in mind that {@code decode(..)} method can be called many
 *     times to decode a single message.  For example, the following code will
 *     not work:
 * <pre> public class MyDecoder extends {@link ReplayingDecoder}&lt;{@link Void}&gt; {
 *
 *   private final Queue&lt;Integer&gt; values = new LinkedList&lt;Integer&gt;();
 *
 *   {@code @Override}
 *   public void decode(.., {@link ByteBuf} buf, List&lt;Object&gt; out) throws Exception {
 *
 *     // A message contains 2 integers.
 *     values.offer(buf.readInt());
 *     values.offer(buf.readInt());
 *
 *     // This assertion will fail intermittently since values.offer()
 *     // can be called more than two times!
 *     assert values.size() == 2;
 *     out.add(values.poll() + values.poll());
 *   }
 * }</pre>
 *      The correct implementation looks like the following, and you can also
 *      utilize the 'checkpoint' feature which is explained in detail in the
 *      next section.
 * <pre> public class MyDecoder extends {@link ReplayingDecoder}&lt;{@link Void}&gt; {
 *
 *   private final Queue&lt;Integer&gt; values = new LinkedList&lt;Integer&gt;();
 *
 *   {@code @Override}
 *   public void decode(.., {@link ByteBuf} buf, List&lt;Object&gt; out) throws Exception {
 *
 *     // Revert the state of the variable that might have been changed
 *     // since the last partial decode.
 *     values.clear();
 *
 *     // A message contains 2 integers.
 *     values.offer(buf.readInt());
 *     values.offer(buf.readInt());
 *
 *     // Now we know this assertion will never fail.
 *     assert values.size() == 2;
 *     out.add(values.poll() + values.poll());
 *   }
 * }</pre>
 *     </li>
 * </ul>
 *
 * <h3>Improving the performance</h3>
 * <p>
 * Fortunately, the performance of a complex decoder implementation can be
 * improved significantly with the {@code checkpoint()} method.  The
 * {@code checkpoint()} method updates the 'initial' position of the buffer so
 * that {@link ReplayingDecoder} rewinds the {@code readerIndex} of the buffer
 * to the last position where you called the {@code checkpoint()} method.
 *
 * <h4>Calling {@code checkpoint(T)} with an {@link Enum}</h4>
 * <p>
 * Although you can just use {@code checkpoint()} method and manage the state
 * of the decoder by yourself, the easiest way to manage the state of the
 * decoder is to create an {@link Enum} type which represents the current state
 * of the decoder and to call {@code checkpoint(T)} method whenever the state
 * changes.  You can have as many states as you want depending on the
 * complexity of the message you want to decode:
 *
 * <pre>
 * public enum MyDecoderState {
 *   READ_LENGTH,
 *   READ_CONTENT;
 * }
 *
 * public class IntegerHeaderFrameDecoder
 *      extends {@link ReplayingDecoder}&lt;<strong>MyDecoderState</strong>&gt; {
 *
 *   private int length;
 *
 *   public IntegerHeaderFrameDecoder() {
 *     // Set the initial state.
 *     <strong>super(MyDecoderState.READ_LENGTH);</strong>
 *   }
 *
 *   {@code @Override}
 *   protected void decode({@link ChannelHandlerContext} ctx,
 *                           {@link ByteBuf} buf, List&lt;Object&gt; out) throws Exception {
 *     switch (state()) {
 *     case READ_LENGTH:
 *       length = buf.readInt();
 *       <strong>checkpoint(MyDecoderState.READ_CONTENT);</strong>
 *     case READ_CONTENT:
 *       ByteBuf frame = buf.readBytes(length);
 *       <strong>checkpoint(MyDecoderState.READ_LENGTH);</strong>
 *       out.add(frame);
 *       break;
 *     default:
 *       throw new Error("Shouldn't reach here.");
 *     }
 *   }
 * }
 * </pre>
 *
 * <h4>Calling {@code checkpoint()} with no parameter</h4>
 * <p>
 * An alternative way to manage the decoder state is to manage it by yourself.
 * <pre>
 * public class IntegerHeaderFrameDecoder
 *      extends {@link ReplayingDecoder}&lt;<strong>{@link Void}</strong>&gt; {
 *
 *   <strong>private boolean readLength;</strong>
 *   private int length;
 *
 *   {@code @Override}
 *   protected void decode({@link ChannelHandlerContext} ctx,
 *                           {@link ByteBuf} buf, List&lt;Object&gt; out) throws Exception {
 *     if (!readLength) {
 *       length = buf.readInt();
 *       <strong>readLength = true;</strong>
 *       <strong>checkpoint();</strong>
 *     }
 *
 *     if (readLength) {
 *       ByteBuf frame = buf.readBytes(length);
 *       <strong>readLength = false;</strong>
 *       <strong>checkpoint();</strong>
 *       out.add(frame);
 *     }
 *   }
 * }
 * </pre>
 *
 * <h3>Replacing a decoder with another decoder in a pipeline</h3>
 * <p>
 * If you are going to write a protocol multiplexer, you will probably want to
 * replace a {@link ReplayingDecoder} (protocol detector) with another
 * {@link ReplayingDecoder}, {@link ByteToMessageDecoder} or {@link MessageToMessageDecoder}
 * (actual protocol decoder).
 * It is not possible to achieve this simply by calling
 * {@link ChannelPipeline#replace(ChannelHandler, String, ChannelHandler)}, but
 * some additional steps are required:
 * <pre>
 * public class FirstDecoder extends {@link ReplayingDecoder}&lt;{@link Void}&gt; {
 *
 *     {@code @Override}
 *     protected void decode({@link ChannelHandlerContext} ctx,
 *                             {@link ByteBuf} buf, List&lt;Object&gt; out) {
 *         ...
 *         // Decode the first message
 *         Object firstMessage = ...;
 *
 *         // Add the second decoder
 *         ctx.pipeline().addLast("second", new SecondDecoder());
 *
 *         if (buf.isReadable()) {
 *             // Hand off the remaining data to the second decoder
 *             out.add(firstMessage);
 *             out.add(buf.readBytes(<b>super.actualReadableBytes()</b>));
 *         } else {
 *             // Nothing to hand off
 *             out.add(firstMessage);
 *         }
 *         // Remove the first decoder (me)
 *         ctx.pipeline().remove(this);
 *     }
 * </pre>
 * @param <S>
 *
 *        the state type which is usually an {@link Enum}; use {@link Void} if state management is
 *        unused
 *  使用上面的Byte2IntegerDecoder会面临一个问题，需要对ByteBuf 的长度进行验查， 如果有足够的字节，才进行整数的读取，这种长度的判断，
 * 是否可以由Netty 帮忙来完成呢？ 安全是，使用Netty的ReplayingDecoder可以省去长度的判断
 *  ReplayingDecoder类是ByteToMessageDocoder的子类，其作用是 。
 *  1. 在读取ByteBuf缓冲区的数据之前，需要检查缓冲区是否有足够的字节
 *  2. 若ByteBuf 中有足够的字节，则会正常的读取，反之，如果没有足够的字节，则会停止解码 。
 *
 *
 * 继承ReplayingDecoder 类实现一个解码器，就不编写长度判断的代码了，ReplayingDecoder 进行长度的判断原理，其实很简单，它的内部定义了一个新的
 * 二进制缓冲区，对ByteBuf缓冲区进行了装饰， 这个类名为ReplayingDecoderBuffer ，该头饰器的特点是， 在缓冲区真正读取数据之前，首先进行长度
 * 的判断，如果长度合格，则读取数据，否则，抛出RepayError ，ReplayingDecoder捕获到ReplayError后，会留着数据，等待下一次的IO事件到来时再
 * 读取。 简单来讲，RepaylingDecoder基类的关键技术就是偷梁换柱，将外部传入的ByteBuf缓冲区传给子类之前，换成也自己的装饰过的RepayingDecoder
 * 的缓冲区， 也就是说，在示例程序中，Byte2IntegerReplayDecoder中的Decoder方法所得到的实参in的值，它的直接类型并不是原始的ByteBuf 类型，而是
 * RepalayingDecoderBuffer类型。
 *
 * RepaylingDecoderBuffer 的类型，首先是一个内部类， 其次它继承了ByteBuf类型，包装了ByteBuf类型的大部分读取方法 ， ReplayingDecoderBuffer
 * 类型的读取方法与ByteBuf类型的读取方法相比，做了什么样的功能增强呢？ 主要是二进制数据长度的判断，如果长度不足，则抛出异常，这个异常
 * 反应过来被ReplayingDeder基类所捕获，将解码工作停掉。
 *
 * ReplayingDecoder的作用，远远不止于进行长度的判断，它更重要的作用是用于分包传输的应用场景 。
 *
 *
 *
 *
 * 小结：通过ReplayingDecoder解码器， 可以正确的解码分包后的ByteBuf 数据包， 但是，在实际开发中， 不太建议继承这个类， 原因是
 * 1. 不是所有的ByteBuf 操作都会被ReplayingDecoderBuffer装饰类所支持，可能有些ByteBuf操作在ReplayingDecoder子类的decoder实现方法中被使用时就会抛出ReplayError异常。
 * 2. 在数据解析逻辑复杂的应用场景中， ReplayingDecoder在解析速度上相对较差
 *  原因是什么呢？ 在ByteBuf中长度不够时， ReplayingDecoder会捕获一个ReplayError异常， 这里会把ByteBuf 的读指针还原为之前的读指针（checkpoint）
 *  然后结束这次解析操作，等待下一次IO读事件，在网络条件比较糟糕的情况下， 一个数据包的解析逻辑会被反复执行多次，如果解析过程是一个消耗CPU
 *  的操作，那么这时CPU是个大负担
 *
 *  所以ReplayingDecoder 更多应用是应用于数据解析逻辑简单的场景，在数据解析复杂的应用场景，建议使用前文介绍的解码器ByteTomMessageDecoder
 *  或者其子类，它们会更加合适
 *
 */
public abstract class ReplayingDecoder<S> extends ByteToMessageDecoder {

    static final Signal REPLAY = Signal.valueOf(ReplayingDecoder.class, "REPLAY");
    // 初始化内部的ByteBuf 缓冲装饰器类
    private final ReplayingDecoderByteBuf replayable = new ReplayingDecoderByteBuf();
    private S state;                            // 重要的成员属性，表示阶段，类型为泛型，默认为Object
    private int checkpoint = -1;

    /**
     * Creates a new instance with no initial state (i.e: {@code null}).
     */
    // 默认的构造器
    protected ReplayingDecoder() {
        this(null);
    }

    /**
     * Creates a new instance with the specified initial state.
     */
    // 重载的构造器
    protected ReplayingDecoder(S initialState) {
        // 状态state 的默认值为null
        state = initialState;
    }

    /**
     * Stores the internal cumulative buffer's reader position.
     */
    protected void checkpoint() {
        checkpoint = internalBuffer().readerIndex();
    }

    /**
     * Stores the internal cumulative buffer's reader position and updates
     * the current decoder state.
     */
    protected void checkpoint(S state) {
        checkpoint();
        state(state);
    }

    /**
     * Returns the current state of this decoder.
     * @return the current state of this decoder
     */
    protected S state() {
        return state;
    }

    /**
     * Sets the current state of this decoder.
     * @return the old state of this decoder
     */
    protected S state(S newState) {
        S oldState = state;
        state = newState;
        return oldState;
    }

    @Override
    final void channelInputClosed(ChannelHandlerContext ctx, List<Object> out) throws Exception {
        try {
            replayable.terminate();
            if (cumulation != null) {
                callDecode(ctx, internalBuffer(), out);
            } else {
                replayable.setCumulation(Unpooled.EMPTY_BUFFER);
            }
            decodeLast(ctx, replayable, out);
        } catch (Signal replay) {
            // Ignore
            replay.expect(REPLAY);
        }
    }

    @Override
    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        replayable.setCumulation(in);
        try {
            while (in.isReadable()) {
                int oldReaderIndex = checkpoint = in.readerIndex();
                int outSize = out.size();

                if (outSize > 0) {
                    fireChannelRead(ctx, out, outSize);
                    out.clear();

                    // Check if this handler was removed before continuing with decoding.
                    // If it was removed, it is not safe to continue to operate on the buffer.
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/4635
                    if (ctx.isRemoved()) {
                        break;
                    }
                    outSize = 0;
                }

                S oldState = state;
                int oldInputLength = in.readableBytes();
                try {
                    decodeRemovalReentryProtection(ctx, replayable, out);

                    // Check if this handler was removed before continuing the loop.
                    // If it was removed, it is not safe to continue to operate on the buffer.
                    //
                    // See https://github.com/netty/netty/issues/1664
                    if (ctx.isRemoved()) {
                        break;
                    }

                    if (outSize == out.size()) {
                        if (oldInputLength == in.readableBytes() && oldState == state) {
                            throw new DecoderException(
                                    StringUtil.simpleClassName(getClass()) + ".decode() must consume the inbound " +
                                    "data or change its state if it did not decode anything.");
                        } else {
                            // Previous data has been discarded or caused state transition.
                            // Probably it is reading on.
                            continue;
                        }
                    }
                } catch (Signal replay) {
                    replay.expect(REPLAY);

                    // Check if this handler was removed before continuing the loop.
                    // If it was removed, it is not safe to continue to operate on the buffer.
                    //
                    // See https://github.com/netty/netty/issues/1664
                    if (ctx.isRemoved()) {
                        break;
                    }

                    // Return to the checkpoint (or oldPosition) and retry.
                    int checkpoint = this.checkpoint;
                    if (checkpoint >= 0) {
                        in.readerIndex(checkpoint);
                    } else {
                        // Called by cleanup() - no need to maintain the readerIndex
                        // anymore because the buffer has been released already.
                    }
                    break;
                }

                if (oldReaderIndex == in.readerIndex() && oldState == state) {
                    throw new DecoderException(
                           StringUtil.simpleClassName(getClass()) + ".decode() method must consume the inbound data " +
                           "or change its state if it decoded something.");
                }
                if (isSingleDecode()) {
                    break;
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception cause) {
            throw new DecoderException(cause);
        }
    }
}
