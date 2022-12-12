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

import static io.netty.util.internal.ObjectUtil.checkPositive;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.internal.StringUtil;

import java.util.List;

/**
 * {@link ChannelInboundHandlerAdapter} which decodes bytes in a stream-like fashion from one {@link ByteBuf} to an
 * other Message type.
 *
 * For example here is an implementation which reads all readable bytes from
 * the input {@link ByteBuf} and create a new {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToMessageDecoder} {
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(in.readBytes(in.readableBytes()));
 *         }
 *     }
 * </pre>
 *
 * <h3>Frame detection</h3>
 * <p>
 * Generally frame detection should be handled earlier in the pipeline by adding a
 * {@link DelimiterBasedFrameDecoder}, {@link FixedLengthFrameDecoder}, {@link LengthFieldBasedFrameDecoder},
 * or {@link LineBasedFrameDecoder}.
 * <p>
 * If a custom frame decoder is required, then one needs to be careful when implementing
 * one with {@link ByteToMessageDecoder}. Ensure there are enough bytes in the buffer for a
 * complete frame by checking {@link ByteBuf#readableBytes()}. If there are not enough bytes
 * for a complete frame, return without modifying the reader index to allow more bytes to arrive.
 * <p>
 * To check for complete frames without modifying the reader index, use methods like {@link ByteBuf#getInt(int)}.
 * One <strong>MUST</strong> use the reader index when using methods like {@link ByteBuf#getInt(int)}.
 * For example calling <tt>in.getInt(0)</tt> is assuming the frame starts at the beginning of the buffer, which
 * is not always the case. Use <tt>in.getInt(in.readerIndex())</tt> instead.
 * <h3>Pitfalls</h3>
 * <p>
 * Be aware that sub-classes of {@link ByteToMessageDecoder} <strong>MUST NOT</strong>
 * annotated with {@link @Sharable}.
 * <p>
 * Some methods such as {@link ByteBuf#readBytes(int)} will cause a memory leak if the returned buffer
 * is not released or added to the <tt>out</tt> {@link List}. Use derived buffers like {@link ByteBuf#readSlice(int)}
 * to avoid leaking memory.
 *
 *
 * 什么叫Netty解码器呢？
 * 首先，它是一个InBound入站处理器，解码器负责处理 入站数据
 * 其次，它能将上一站InBound 入站处理器传过来输入（Input）数据，进行数据的解码或者格式转换，然后输出（Output ）到一下站Inbound入站处理器。
 * 一个标准的解码器将输入类型为ByteBuf 缓冲区的数据进行解码，输出一个一个的Java POJO对象，Netty内置了这个解码器， 叫作ByteToMessageDecoder
 * ，位在Netty 的io.netty.handler.codec包中。
 *
 * 强调一下， 所有的Netty 中的解码器， 都是Inbound入站处理器类型，都直接或者间接的实现了ChannelInBoundHandler接口。
 *
 * ByteToMessageDecoder是一个非常重要的解码器基类， 它是一个抽象类， 实现了解码的基础逻辑和流程，ByteToMessageDecoder继承自ChannelInBoundHandlerAdapter
 * 适配器， 是一个入站处理器，实现了从ByteBuf 到Java POJO对象的解码功能 。
 *
 * ByteToMessageDecoder 解码的流程，大致如图7-1所示 ， 具体可以描述为，首先，它将上一站传过来的输入到ByteBuf 的数据进行解码，
 * 解码出一个List<Object> 对象列表，然后，迭代List<Object>列表 ， 逐个将Java POJO 对象传入到下一站Inbound入站处理器。
 *
 *  查看到了Nerry源码，我们会以其的发现，ByteToMessageDecoder 是一个抽象类， 不能以实例化的方式创建对象，也就是说，直接通过ByteToMessageDecoder
 *  类，并不能完成ByteBuf 字节码到具体的Java类型的解码，还得依赖于它的具体实现。
 *
 *  ByteToMessageDecoder 的解码方法名为decode，通过源代码我们可以发现，decode方法只是提供了一个抽象的方法，也就是说，decode方法中的具体解码过程
 *  ByteToMessageDecoder 没有具体的实现， 换句话说，如何将Bytebuf数据变成Object 对象，需要子类去完成 ，父类先不管 。
 *  总之，作为解码器的父类，ByteToMessageDecoder 仅仅提供了一个流程性的框架，它仅仅将子类的decode方法解码后的Object结果，放入到自己的内部结构列表
 *  List<Object>中，最终父类会负责将<Object> 中的元素，一个一个的传递给一一步，哦，不对，父类还没有那么 努力 ， 而是将子类的Oject结果放入到
 *  父类的List<Object> 列表中，也交由子类的decode 方法完成的。
 *
 *  如果要实现和个自己的解码器 首先继承ByteToMessageDecoder抽象类，然后，实现其蕨类的decode的抽象方法，将解码的逻辑，写入到此方法
 *  总体来说，如果要实现一个自己的ByteBuf 解码器，流程大致如下
 *
 *  1. 首先继承了ByteToMessageDecoder抽象类
 *  2. 然后实现其基类的decode抽象方法 ， 将ByteBuf到POJO的解码的逻辑写入到此方法中，将ByteBuf 二进制数据，解码成一个一个的POJO对象
 *  3. 在子类的decode方法中，需要将解码后的Java POJO 对象，放入到decode的List<Object> 实参中，这个参数是ByteToMessageDecoder父类
 *  传入的，也就是父类的结果收集列表
 *
 *  在流水线的过程中，ByteToMessageDecoder 调用子类的decode方法解码完成后，会将List<Object>中的结果，一个一个的地分开传递到一下站
 *  Inbound入站，处理器。
 *
 *
 *
 */
public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {

    /**
     * Cumulate {@link ByteBuf}s by merge them into one {@link ByteBuf}'s, using memory copies.
     *
     */
    public static final Cumulator MERGE_CUMULATOR = new Cumulator() {
        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            try {
                final ByteBuf buffer;
                // 判断是否需要扩容 ， 其逻辑与组合缓冲区类似
                if (cumulation.writerIndex() > cumulation.maxCapacity() - in.readableBytes()
                    || cumulation.refCnt() > 1 || cumulation.isReadOnly()) {
                    // Expand cumulation (by replace it) when either there is not more room in the buffer
                    // or if the refCnt is greater then 1 which may happen when the user use slice().retain() or
                    // duplicate().retain() or if its read-only.
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/2327
                    // - https://github.com/netty/netty/issues/1764
                    buffer = expandCumulation(alloc, cumulation, in.readableBytes());
                } else {
                    buffer = cumulation;
                }
                // 把需要解码的字节写入读半包字节容器中
                buffer.writeBytes(in);
                return buffer;
            } finally {
                // We must release in in all cases as otherwise it may produce a leak if writeBytes(...) throw
                // for whatever release (for example because of OutOfMemoryError)
                // 非组合缓冲区，需要释放buf
                in.release();
            }
        }
    };

    /**
     * Cumulate {@link ByteBuf}s by add them to a {@link CompositeByteBuf} and so do no memory copy whenever possible.
     * Be aware that {@link CompositeByteBuf} use a more complex indexing implementation so depending on your use-case
     * and the decoder implementation this may be slower then just use the {@link #MERGE_CUMULATOR}.
     * 复合缓冲区实现读半包字节容器
     */
    public static final Cumulator COMPOSITE_CUMULATOR = new Cumulator() {
        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            ByteBuf buffer;
            try {
                /***
                 * 引用大于1，说明用户使用了slice().retain()或duplicate().retain()
                 * 命名refCnt增加且大于1
                 * 此时扩容返回一个新的累积区ByteBuf
                 * 以便对老的累积区ByteBuf进行后续的处理
                 */
                if (cumulation.refCnt() > 1) {
                    // Expand cumulation (by replace it) when the refCnt is greater then 1 which may happen when the
                    // user use slice().retain() or duplicate().retain().
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/2327
                    // - https://github.com/netty/netty/issues/1764
                    buffer = expandCumulation(alloc, cumulation, in.readableBytes());
                    buffer.writeBytes(in);
                } else {
                    CompositeByteBuf composite;
                    if (cumulation instanceof CompositeByteBuf) {
                        composite = (CompositeByteBuf) cumulation;
                    } else {
                        // 创建CompositeByteBuf ,把cumulation包装成Component元素
                        // 并将其加入到复合缓冲区中
                        composite = alloc.compositeBuffer(Integer.MAX_VALUE);
                        composite.addComponent(true, cumulation);
                    }
                    // 把ByteBuf也加入到复合缓冲区中
                    composite.addComponent(true, in);
                    // 赋空值，由于ByteBuf 加入复合缓冲区后没有调用retain()方法，因此无须释放
                    in = null;
                    buffer = composite;
                }
                return buffer;
            } finally {
                // in 不为null,说明调用了buffer的writeBytes()方法，此时必须释放内存，以防止内存泄漏
                if (in != null) {
                    // We must release if the ownership was not transferred as otherwise it may produce a leak if
                    // writeBytes(...) throw for whatever release (for example because of OutOfMemoryError).
                    in.release();
                }
            }
        }
    };

    private static final byte STATE_INIT = 0;
    private static final byte STATE_CALLING_CHILD_DECODE = 1;
    private static final byte STATE_HANDLER_REMOVED_PENDING = 2;

    ByteBuf cumulation;
    private Cumulator cumulator = MERGE_CUMULATOR;
    private boolean singleDecode;
    private boolean first;

    /**
     * This flag is used to determine if we need to call {@link ChannelHandlerContext#read()} to consume more data
     * when {@link ChannelConfig#isAutoRead()} is {@code false}.
     */
    private boolean firedChannelRead;

    /**
     * A bitmask where the bits are defined as
     * <ul>
     *     <li>{@link #STATE_INIT}</li>
     *     <li>{@link #STATE_CALLING_CHILD_DECODE}</li>
     *     <li>{@link #STATE_HANDLER_REMOVED_PENDING}</li>
     * </ul>
     */
    private byte decodeState = STATE_INIT;
    private int discardAfterReads = 16;
    private int numReads;

    protected ByteToMessageDecoder() {
        ensureNotSharable();
    }

    /**
     * If set then only one message is decoded on each {@link #channelRead(ChannelHandlerContext, Object)}
     * call. This may be useful if you need to do some protocol upgrade and want to make sure nothing is mixed up.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public void setSingleDecode(boolean singleDecode) {
        this.singleDecode = singleDecode;
    }

    /**
     * If {@code true} then only one message is decoded on each
     * {@link #channelRead(ChannelHandlerContext, Object)} call.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public boolean isSingleDecode() {
        return singleDecode;
    }

    /**
     * Set the {@link Cumulator} to use for cumulate the received {@link ByteBuf}s.
     */
    public void setCumulator(Cumulator cumulator) {
        if (cumulator == null) {
            throw new NullPointerException("cumulator");
        }
        this.cumulator = cumulator;
    }

    /**
     * Set the number of reads after which {@link ByteBuf#discardSomeReadBytes()} are called and so free up memory.
     * The default is {@code 16}.
     */
    public void setDiscardAfterReads(int discardAfterReads) {
        checkPositive(discardAfterReads, "discardAfterReads");
        this.discardAfterReads = discardAfterReads;
    }

    /**
     * Returns the actual number of readable bytes in the internal cumulative
     * buffer of this decoder. You usually do not need to rely on this value
     * to write a decoder. Use it only when you must use it at your own risk.
     * This method is a shortcut to {@link #internalBuffer() internalBuffer().readableBytes()}.
     */
    protected int actualReadableBytes() {
        return internalBuffer().readableBytes();
    }

    /**
     * Returns the internal cumulative buffer of this decoder. You usually
     * do not need to access the internal buffer directly to write a decoder.
     * Use it only when you must use it at your own risk.
     */
    protected ByteBuf internalBuffer() {
        if (cumulation != null) {
            return cumulation;
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (decodeState == STATE_CALLING_CHILD_DECODE) {
            decodeState = STATE_HANDLER_REMOVED_PENDING;
            return;
        }
        ByteBuf buf = cumulation;
        if (buf != null) {
            // Directly set this to null so we are sure we not access it in any other method here anymore.
            cumulation = null;
            numReads = 0;
            int readable = buf.readableBytes();
            if (readable > 0) {
                ByteBuf bytes = buf.readBytes(readable);
                buf.release();
                ctx.fireChannelRead(bytes);
                ctx.fireChannelReadComplete();
            } else {
                buf.release();
            }
        }
        handlerRemoved0(ctx);
    }

    /**
     * Gets called after the {@link ByteToMessageDecoder} was removed from the actual context and it doesn't handle
     * events anymore.
     */
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception { }

    @Override
    // 图5-5描述了Netty的解码器设计，采用了模板设计模式。对于用 户选择的解码器，除了MessageToMessageCodec的子类，其他解码器首
    // 先 都 会 经 过 其 父 类 ByteToMessageDecoder 的 channelRead() 方 法 。 channelRead()及相关方法的源码解读如下:
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 1. channelRead()方法首先会判断msg是否为ByteBuf类型，只 有在是的情况下才会进行解码。这也是为什么将StringDecoder等
        // MessageToMessageCodec解码器放在ByteToMessageDecoder子类解码器 后面的原因，这时的msg一般是堆外直接内存DirectByteBuf，
        // 因为采 用堆外直接内存在传输时可以少一次复制。然后判断是否为第一次解 码，若是，则直接把msg赋值给cumulation(cumulation是读半包字节
        // 容器);若不是，则需要把msg写入cumulation中，写入之前要判断是 否需要扩容。
        if (msg instanceof ByteBuf) {
            // 解码后消息列表
            CodecOutputList out = CodecOutputList.newInstance();
            try {
                ByteBuf data = (ByteBuf) msg;
                // 是否为第一次解码
                first = cumulation == null;
                if (first) {
                    // 在第一次解码时需要把data(data是msg的类型强转)赋给字节容器即可
                    cumulation = data;
                } else {
                    // 若不是第一次解码，则需要把msg 写入到cumulation中，写入前需要判断是否需要扩容
                    cumulation = cumulator.cumulate(ctx.alloc(), cumulation, data);
                }
                // 从cumulation字节中解码出消息
                // 2. 把新读取到的数据写入cumulation后，调用callDecode()方 法。在callDecode()方法中会不断地调用子类的decode()方法，
                // 直到 当前cumulation无法继续解码。无法继续解码分两种情况:第一种情 况是无可读字节;第二种情况是经历过decode()方法后，可读字节数 没有任何变化。
                callDecode(ctx, cumulation, out);
            } catch (DecoderException e) {
                throw e;
            } catch (Exception e) {
                throw new DecoderException(e);

            } finally {
                // 3. 执行完callDecode()方法后，进入finally代码块进行收尾 工作。若cumulation不为空，且不可读时，需要把cumulation释放掉
                // 并赋空值，若连续16次(discardAfterReads的默认值)字节容器 cumulation中仍然有未被业务拆包器读取的数据，则需要进行一次压缩:
                // 将有效数据段整体移到容器首部，同时用一个成员变量 firedChannelRead来标识本次读取数据是否拆到了一个业务数据包，
                // 并触发fireChannelRead事件，将拆到的业务数据包传递给后续的 Handler，最后把out放回对象池中。

                // 当字节容器不为空且不可读时，需要释放，并置空，直接回收，将下次解码认为是第一次
                if (cumulation != null && !cumulation.isReadable()) {
                    numReads = 0;
                    cumulation.release();
                    cumulation = null;
                } else if (++ numReads >= discardAfterReads) {
                    // We did enough reads already try to discard some bytes so we not risk to see a OOME.
                    // See https://github.com/netty/netty/issues/4275
                    // 如果读取的字节数大于或等于discardAfterReads,则设置读取字节数为0 ， 并移除字节容器中的一部分读取的字节
                    numReads = 0;
                    discardSomeReadBytes();
                }

                int size = out.size();
                // firedChannelRead 属性在channelReadComplete()方法中被调用
                firedChannelRead |= out.insertSinceRecycled();
                // 遍历解码消息集合，转发消息到下一个Handler 处理器中
                fireChannelRead(ctx, out, size);
                // 回收解码消息集合，以便下次循环利用
                out.recycle();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Get {@code numElements} out of the {@link List} and forward these through the pipeline.
     */
    static void fireChannelRead(ChannelHandlerContext ctx, List<Object> msgs, int numElements) {
        if (msgs instanceof CodecOutputList) {
            fireChannelRead(ctx, (CodecOutputList) msgs, numElements);
        } else {
            for (int i = 0; i < numElements; i++) {
                ctx.fireChannelRead(msgs.get(i));
            }
        }
    }

    /**
     * Get {@code numElements} out of the {@link CodecOutputList} and forward these through the pipeline.
     */
    static void fireChannelRead(ChannelHandlerContext ctx, CodecOutputList msgs, int numElements) {
        for (int i = 0; i < numElements; i ++) {
            ctx.fireChannelRead(msgs.getUnsafe(i));
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        numReads = 0;
        discardSomeReadBytes();
        if (!firedChannelRead && !ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
        firedChannelRead = false;
        ctx.fireChannelReadComplete();
    }

    protected final void discardSomeReadBytes() {
        if (cumulation != null && !first && cumulation.refCnt() == 1) {
            // discard some bytes if possible to make more room in the
            // buffer but only if the refCnt == 1  as otherwise the user may have
            // used slice().retain() or duplicate().retain().
            //
            // See:
            // - https://github.com/netty/netty/issues/2327
            // - https://github.com/netty/netty/issues/1764
            cumulation.discardSomeReadBytes();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channelInputClosed(ctx, true);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ChannelInputShutdownEvent) {
            // The decodeLast method is invoked when a channelInactive event is encountered.
            // This method is responsible for ending requests in some situations and must be called
            // when the input has been shutdown.
            channelInputClosed(ctx, false);
        }
        super.userEventTriggered(ctx, evt);
    }

    private void channelInputClosed(ChannelHandlerContext ctx, boolean callChannelInactive) throws Exception {
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            channelInputClosed(ctx, out);
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            try {
                if (cumulation != null) {
                    cumulation.release();
                    cumulation = null;
                }
                int size = out.size();
                fireChannelRead(ctx, out, size);
                if (size > 0) {
                    // Something was read, call fireChannelReadComplete()
                    ctx.fireChannelReadComplete();
                }
                if (callChannelInactive) {
                    ctx.fireChannelInactive();
                }
            } finally {
                // Recycle in all cases
                out.recycle();
            }
        }
    }

    /**
     * Called when the input of the channel was closed which may be because it changed to inactive or because of
     * {@link ChannelInputShutdownEvent}.
     */
    void channelInputClosed(ChannelHandlerContext ctx, List<Object> out) throws Exception {
        if (cumulation != null) {
            callDecode(ctx, cumulation, out);
            decodeLast(ctx, cumulation, out);
        } else {
            decodeLast(ctx, Unpooled.EMPTY_BUFFER, out);
        }
    }

    /**
     * Called once data should be decoded from the given {@link ByteBuf}. This method will call
     * {@link #decode(ChannelHandlerContext, ByteBuf, List)} as long as decoding should take place.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     */
    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        try {
            // 循环解码
            while (in.isReadable()) {
                int outSize = out.size();
                // 判断是否已经有可用的消息
                if (outSize > 0) {
                    // 触发下一个Handler 去处理这些解码出来的消息
                    fireChannelRead(ctx, out, outSize);
                    out.clear();

                    // Check if this handler was removed before continuing with decoding.
                    // If it was removed, it is not safe to continue to operate on the buffer.
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/4635
                    // 检测Handler是否被从通道处理器上下文移除了，若被移除了，则不能继续操作
                    if (ctx.isRemoved()) {
                        break;
                    }
                    outSize = 0;
                }
                // 获取字节容器的可读字节数
                int oldInputLength = in.readableBytes();
                // 解码字节buf中的数据为消息对象，并将其放入out 中，如果解码器被从通道处理器上下文移除了，则处理移除事件
                decodeRemovalReentryProtection(ctx, in, out);

                // Check if this handler was removed before continuing the loop.
                // If it was removed, it is not safe to continue to operate on the buffer.
                //
                // See https://github.com/netty/netty/issues/1664
                if (ctx.isRemoved()) {
                    break;
                }

                if (outSize == out.size()) {
                    // 如果可读字节数无变化，则说明解码失败，无须继续解码
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }
                // 异常
                if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                                    ".decode() did not read anything but decoded a message.");
                }
                // 是否只能解码一次
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

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     * @throws Exception    is thrown if an error occurs
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     * @throws Exception    is thrown if an error occurs
     */
    final void decodeRemovalReentryProtection(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        decodeState = STATE_CALLING_CHILD_DECODE;
        try {
            // 由子类完成
            decode(ctx, in, out);
        } finally {
            // Channel 的处理器是否正在移除
            boolean removePending = decodeState == STATE_HANDLER_REMOVED_PENDING;
            decodeState = STATE_INIT;
            if (removePending) {
                // 处理Handler 从通道处理器移除事件
                handlerRemoved(ctx);
            }
        }
    }

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, ByteBuf, List)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.isReadable()) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decodeRemovalReentryProtection(ctx, in, out);
        }
    }

    static ByteBuf expandCumulation(ByteBufAllocator alloc, ByteBuf cumulation, int readable) {
        ByteBuf oldCumulation = cumulation;
        cumulation = alloc.buffer(oldCumulation.readableBytes() + readable);
        cumulation.writeBytes(oldCumulation);
        oldCumulation.release();
        return cumulation;
    }

    /**
     * Cumulate {@link ByteBuf}s.
     */
    public interface Cumulator {
        /**
         * Cumulate the given {@link ByteBuf}s and return the {@link ByteBuf} that holds the cumulated bytes.
         * The implementation is responsible to correctly handle the life-cycle of the given {@link ByteBuf}s and so
         * call {@link ByteBuf#release()} if a {@link ByteBuf} is fully consumed.
         */
        ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in);
    }
}
