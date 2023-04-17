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
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import java.nio.ByteOrder;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.serialization.ObjectDecoder;

/**
 * A decoder that splits the received {@link ByteBuf}s dynamically by the
 * value of the length field in the message.  It is particularly useful when you
 * decode a binary message which has an integer header field that represents the
 * length of the message body or the whole message.
 * <p>
 * {@link LengthFieldBasedFrameDecoder} has many configuration parameters so
 * that it can decode any message with a length field, which is often seen in
 * proprietary client-server protocols. Here are some example that will give
 * you the basic idea on which option does what.
 *
 * <h3>2 bytes length field at offset 0, do not strip header</h3>
 *
 * The value of the length field in this example is <tt>12 (0x0C)</tt> which
 * represents the length of "HELLO, WORLD".  By default, the decoder assumes
 * that the length field represents the number of the bytes that follows the
 * length field.  Therefore, it can be decoded with the simplistic parameter
 * combination.
 * <pre>
 * <b>lengthFieldOffset</b>   = <b>0</b>
 * <b>lengthFieldLength</b>   = <b>2</b>
 * lengthAdjustment    = 0
 * initialBytesToStrip = 0 (= do not strip header)
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (14 bytes)
 * +--------+----------------+      +--------+----------------+
 * | Length | Actual Content |----->| Length | Actual Content |
 * | 0x000C | "HELLO, WORLD" |      | 0x000C | "HELLO, WORLD" |
 * +--------+----------------+      +--------+----------------+
 * </pre>
 *
 * <h3>2 bytes length field at offset 0, strip header</h3>
 *
 * Because we can get the length of the content by calling
 * {@link ByteBuf#readableBytes()}, you might want to strip the length
 * field by specifying <tt>initialBytesToStrip</tt>.  In this example, we
 * specified <tt>2</tt>, that is same with the length of the length field, to
 * strip the first two bytes.
 * <pre>
 * lengthFieldOffset   = 0
 * lengthFieldLength   = 2
 * lengthAdjustment    = 0
 * <b>initialBytesToStrip</b> = <b>2</b> (= the length of the Length field)
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (12 bytes)
 * +--------+----------------+      +----------------+
 * | Length | Actual Content |----->| Actual Content |
 * | 0x000C | "HELLO, WORLD" |      | "HELLO, WORLD" |
 * +--------+----------------+      +----------------+
 * </pre>
 *
 * <h3>2 bytes length field at offset 0, do not strip header, the length field
 *     represents the length of the whole message</h3>
 *
 * In most cases, the length field represents the length of the message body
 * only, as shown in the previous examples.  However, in some protocols, the
 * length field represents the length of the whole message, including the
 * message header.  In such a case, we specify a non-zero
 * <tt>lengthAdjustment</tt>.  Because the length value in this example message
 * is always greater than the body length by <tt>2</tt>, we specify <tt>-2</tt>
 * as <tt>lengthAdjustment</tt> for compensation.
 * <pre>
 * lengthFieldOffset   =  0
 * lengthFieldLength   =  2
 * <b>lengthAdjustment</b>    = <b>-2</b> (= the length of the Length field)
 * initialBytesToStrip =  0
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (14 bytes)
 * +--------+----------------+      +--------+----------------+
 * | Length | Actual Content |----->| Length | Actual Content |
 * | 0x000E | "HELLO, WORLD" |      | 0x000E | "HELLO, WORLD" |
 * +--------+----------------+      +--------+----------------+
 * </pre>
 *
 * <h3>3 bytes length field at the end of 5 bytes header, do not strip header</h3>
 *
 * The following message is a simple variation of the first example.  An extra
 * header value is prepended to the message.  <tt>lengthAdjustment</tt> is zero
 * again because the decoder always takes the length of the prepended data into
 * account during frame length calculation.
 * <pre>
 * <b>lengthFieldOffset</b>   = <b>2</b> (= the length of Header 1)
 * <b>lengthFieldLength</b>   = <b>3</b>
 * lengthAdjustment    = 0
 * initialBytesToStrip = 0
 *
 * BEFORE DECODE (17 bytes)                      AFTER DECODE (17 bytes)
 * +----------+----------+----------------+      +----------+----------+----------------+
 * | Header 1 |  Length  | Actual Content |----->| Header 1 |  Length  | Actual Content |
 * |  0xCAFE  | 0x00000C | "HELLO, WORLD" |      |  0xCAFE  | 0x00000C | "HELLO, WORLD" |
 * +----------+----------+----------------+      +----------+----------+----------------+
 * </pre>
 *
 * <h3>3 bytes length field at the beginning of 5 bytes header, do not strip header</h3>
 *
 * This is an advanced example that shows the case where there is an extra
 * header between the length field and the message body.  You have to specify a
 * positive <tt>lengthAdjustment</tt> so that the decoder counts the extra
 * header into the frame length calculation.
 * <pre>
 * lengthFieldOffset   = 0
 * lengthFieldLength   = 3
 * <b>lengthAdjustment</b>    = <b>2</b> (= the length of Header 1)
 * initialBytesToStrip = 0
 *
 * BEFORE DECODE (17 bytes)                      AFTER DECODE (17 bytes)
 * +----------+----------+----------------+      +----------+----------+----------------+
 * |  Length  | Header 1 | Actual Content |----->|  Length  | Header 1 | Actual Content |
 * | 0x00000C |  0xCAFE  | "HELLO, WORLD" |      | 0x00000C |  0xCAFE  | "HELLO, WORLD" |
 * +----------+----------+----------------+      +----------+----------+----------------+
 * </pre>
 *
 * <h3>2 bytes length field at offset 1 in the middle of 4 bytes header,
 *     strip the first header field and the length field</h3>
 *
 * This is a combination of all the examples above.  There are the prepended
 * header before the length field and the extra header after the length field.
 * The prepended header affects the <tt>lengthFieldOffset</tt> and the extra
 * header affects the <tt>lengthAdjustment</tt>.  We also specified a non-zero
 * <tt>initialBytesToStrip</tt> to strip the length field and the prepended
 * header from the frame.  If you don't want to strip the prepended header, you
 * could specify <tt>0</tt> for <tt>initialBytesToSkip</tt>.
 * <pre>
 * lengthFieldOffset   = 1 (= the length of HDR1)
 * lengthFieldLength   = 2
 * <b>lengthAdjustment</b>    = <b>1</b> (= the length of HDR2)
 * <b>initialBytesToStrip</b> = <b>3</b> (= the length of HDR1 + LEN)
 *
 * BEFORE DECODE (16 bytes)                       AFTER DECODE (13 bytes)
 * +------+--------+------+----------------+      +------+----------------+
 * | HDR1 | Length | HDR2 | Actual Content |----->| HDR2 | Actual Content |
 * | 0xCA | 0x000C | 0xFE | "HELLO, WORLD" |      | 0xFE | "HELLO, WORLD" |
 * +------+--------+------+----------------+      +------+----------------+
 * </pre>
 *
 * <h3>2 bytes length field at offset 1 in the middle of 4 bytes header,
 *     strip the first header field and the length field, the length field
 *     represents the length of the whole message</h3>
 *
 * Let's give another twist to the previous example.  The only difference from
 * the previous example is that the length field represents the length of the
 * whole message instead of the message body, just like the third example.
 * We have to count the length of HDR1 and Length into <tt>lengthAdjustment</tt>.
 * Please note that we don't need to take the length of HDR2 into account
 * because the length field already includes the whole header length.
 * <pre>
 * lengthFieldOffset   =  1
 * lengthFieldLength   =  2
 * <b>lengthAdjustment</b>    = <b>-3</b> (= the length of HDR1 + LEN, negative)
 * <b>initialBytesToStrip</b> = <b> 3</b>
 *
 * BEFORE DECODE (16 bytes)                       AFTER DECODE (13 bytes)
 * +------+--------+------+----------------+      +------+----------------+
 * | HDR1 | Length | HDR2 | Actual Content |----->| HDR2 | Actual Content |
 * | 0xCA | 0x0010 | 0xFE | "HELLO, WORLD" |      | 0xFE | "HELLO, WORLD" |
 * +------+--------+------+----------------+      +------+----------------+
 * </pre>
 * @see LengthFieldPrepender
 * 另一种是根据写入消息体长度值解码(如LengthFieldBasedFrameDecoder)，这种解码器的一般用法 是先读取前面4个字节的int值，再根据这个值去读取可用的数据包。
 *
 *
 *
 *
 *
 * 自定义长度数据包解码器
 * 这是一种基于灵活长度的解码器，在ByteBuf数据包中， 加一个长度的字段，保存了原始数据包的长度，解码的时候，会按照这个长度进行原始数据包的
 * 提取。
 * 这种解码器在所有的开箱即用的解码器中最为复杂的一种， 后面会重点介绍 。
 *
 *
 * 长度字段数据包解码器
 *
 * 可以看 Netty LengthFieldBasedFrameDecoder源码分析 这篇博客  https://blog.csdn.net/weixin_45271492/article/details/125347939
 */
public class LengthFieldBasedFrameDecoder extends ByteToMessageDecoder {
    // 大小端排序
    // 大端模式 ： 是指数据的高字节保存在内存的地址中， 而数据的低字节保存在内存的高地址中，地址由小向大增加，而数据从高位往低位放
    // 小端模式 ： 是指数据的高字节保存在内存的高地址中， 而数据的低字节保存在内存的低地址中， 高地址部分权值高，低地址部分权值低，和我们日常的逻辑方法一致
    private final ByteOrder byteOrder;
    // 最大帧长度
    private final int maxFrameLength;
    // 长度字节偏移量
    private final int lengthFieldOffset;
    // 长度域字节的字节数
    private final int lengthFieldLength;
    // 长度字节结束位置的偏移量， lengthFieldEndOffset + lengthFieldLength
    private final int lengthFieldEndOffset;
    // 长度调整
    private final int lengthAdjustment;
    // 需要跳过的字节数
    private final int initialBytesToStrip;
    // 快速失败
    private final boolean failFast;
    // true 表示开启丢弃模式，false表示正常模式
    private boolean discardingTooLongFrame;
    // 当某个数据包的长度超过maxLength ， 则开启丢弃模式，此字节记录需要丢弃的数据长度
    private long tooLongFrameLength;
    // 记录还剩余多少字节需要丢弃
    private long bytesToDiscard;

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength
     *        the maximum length of the frame.  If the length of the frame is
     *        greater than this value, {@link TooLongFrameException} will be
     *        thrown.
     * @param lengthFieldOffset
     *        the offset of the length field
     * @param lengthFieldLength
     *        the length of the length field
     */
    public LengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthFieldOffset, int lengthFieldLength) {
        this(maxFrameLength, lengthFieldOffset, lengthFieldLength, 0, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength
     *        the maximum length of the frame.  If the length of the frame is
     *        greater than this value, {@link TooLongFrameException} will be
     *        thrown.
     * @param lengthFieldOffset
     *        the offset of the length field
     * @param lengthFieldLength
     *        the length of the length field
     * @param lengthAdjustment
     *        the compensation value to add to the value of the length field
     * @param initialBytesToStrip
     *        the number of first bytes to strip out from the decoded frame
     */
    // maxFrameLength : 发送的数据包最大长度， 发送数据包的最大长度，例如1024，表示一个数据包最多可发送1024个字节
    // lengthFieldOffset: 长度字段的偏移量， 指的是长度字段位于数据包内部字节数组中的下标值
    // lengthFieldLength: 长度字段自己占用的字节数，如果长度字段是一个int整数，则为4，如果长度字段是一个short整数，则为2
    // lengthAdjustment: 长度字段的偏移量矫正， 这个参数最为难懂，在传输协议比较复杂的情况下，例如包含了长度字段，协议版本号， 魔数等
    //                  那么解码时，就需要进行长度字段的矫正，长度矫正值的计算公式为：内容字段偏移量 - 长度字段偏移量 - 长度字段的字节数
    //
    // initialBytesToStrip: 丢弃的起始字节数 ， 在有效数据字段Context 前面，还有一些其他的字段的字节，作为最终的解析结果，可以丢弃。
    // 例如，上面的示例程序中， 前面有4个字节的长度字段，起到辅助作用，最终的结果中不需要这个长度，所以丢弃字节数为4 。
    // LengthFieldBasedFrameDecoder spliter =
    //                    new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4)
    // 第1个参数 maxFrameLength设置为1024，表示数据包的最大长度1024
    // 第2个参数 lengthFieldOffset 设置为0 ， 表示长度字段的偏移量为0 ， 也就是长度字段放在了最前面， 处理数据包的起始位置 。
    // 第3个参数 lengthFieldLength 设置为4，表示长度字段的长度为4个字节，即表示内容长度值占用数据包的4个字节 。
    // 第4个参数 lengthAdjustment 设置为0，长度调整值的计算公式为， 内容字段偏移量-长度字节的偏移量-长度字节的字节数 在上面的示例程序中实际的值为4-0-4 = 0
    // 第5个参数 initialBytesToStrip 为4， 表示获取最终内容Content的字节数组时， 抛弃最前面的4个字节的数据 。
    public LengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip) {
        this(
                maxFrameLength,
                lengthFieldOffset, lengthFieldLength, lengthAdjustment,
                initialBytesToStrip, true);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength
     *        the maximum length of the frame.  If the length of the frame is
     *        greater than this value, {@link TooLongFrameException} will be
     *        thrown.
     * @param lengthFieldOffset
     *        the offset of the length field
     * @param lengthFieldLength
     *        the length of the length field
     * @param lengthAdjustment
     *        the compensation value to add to the value of the length field
     * @param initialBytesToStrip
     *        the number of first bytes to strip out from the decoded frame
     * @param failFast
     *        If <tt>true</tt>, a {@link TooLongFrameException} is thrown as
     *        soon as the decoder notices the length of the frame will exceed
     *        <tt>maxFrameLength</tt> regardless of whether the entire frame
     *        has been read.  If <tt>false</tt>, a {@link TooLongFrameException}
     *        is thrown after the entire frame that exceeds <tt>maxFrameLength</tt>
     *        has been read.
     */
    public LengthFieldBasedFrameDecoder(
            int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        this(
                ByteOrder.BIG_ENDIAN, maxFrameLength, lengthFieldOffset, lengthFieldLength,
                lengthAdjustment, initialBytesToStrip, failFast);
    }

    /**
     * Creates a new instance.
     *
     * @param byteOrder
     *        the {@link ByteOrder} of the length field
     * @param maxFrameLength
     *        the maximum length of the frame.  If the length of the frame is
     *        greater than this value, {@link TooLongFrameException} will be
     *        thrown.
     * @param lengthFieldOffset
     *        the offset of the length field
     * @param lengthFieldLength
     *        the length of the length field
     * @param lengthAdjustment
     *        the compensation value to add to the value of the length field
     * @param initialBytesToStrip
     *        the number of first bytes to strip out from the decoded frame
     * @param failFast
     *        If <tt>true</tt>, a {@link TooLongFrameException} is thrown as
     *        soon as the decoder notices the length of the frame will exceed
     *        <tt>maxFrameLength</tt> regardless of whether the entire frame
     *        has been read.  If <tt>false</tt>, a {@link TooLongFrameException}
     *        is thrown after the entire frame that exceeds <tt>maxFrameLength</tt>
     *        has been read.
     */
    public LengthFieldBasedFrameDecoder(
            ByteOrder byteOrder, int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        if (byteOrder == null) {
            throw new NullPointerException("byteOrder");
        }

        checkPositive(maxFrameLength, "maxFrameLength");

        checkPositiveOrZero(lengthFieldOffset, "lengthFieldOffset");

        checkPositiveOrZero(initialBytesToStrip, "initialBytesToStrip");

        if (lengthFieldOffset > maxFrameLength - lengthFieldLength) {
            throw new IllegalArgumentException(
                    "maxFrameLength (" + maxFrameLength + ") " +
                    "must be equal to or greater than " +
                    "lengthFieldOffset (" + lengthFieldOffset + ") + " +
                    "lengthFieldLength (" + lengthFieldLength + ").");
        }

        this.byteOrder = byteOrder;
        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
        this.initialBytesToStrip = initialBytesToStrip;
        this.failFast = failFast;
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    private void discardingTooLongFrame(ByteBuf in) {
        // 保存还需要丢弃多少字节
        long bytesToDiscard = this.bytesToDiscard;
        // 获取当前可以丢弃的字节数， 有可能出现半包情况
        int localBytesToDiscard = (int) Math.min(bytesToDiscard, in.readableBytes());
        // 丢弃
        in.skipBytes(localBytesToDiscard);
        // 更新还需要丢弃的字节数
        bytesToDiscard -= localBytesToDiscard;
        this.bytesToDiscard = bytesToDiscard;
        // 是否需要快速失败，回到上面的逻辑
        failIfNecessary(false);
    }

    private static void failOnNegativeLengthField(ByteBuf in, long frameLength, int lengthFieldEndOffset) {
        in.skipBytes(lengthFieldEndOffset);
        throw new CorruptedFrameException(
           "negative pre-adjustment length field: " + frameLength);
    }

    private static void failOnFrameLengthLessThanLengthFieldEndOffset(ByteBuf in,
                                                                      long frameLength,
                                                                      int lengthFieldEndOffset) {
        in.skipBytes(lengthFieldEndOffset);
        throw new CorruptedFrameException(
           "Adjusted frame length (" + frameLength + ") is less " +
              "than lengthFieldEndOffset: " + lengthFieldEndOffset);
    }

    // frameLength ：数据包的长度
    private void exceededFrameLength(ByteBuf in, long frameLength) {
        // 数据包长度-可读字节数 两种模式
        // 1.数据包总长度为100，可读字节数为50 ， 说明还剩下50个字节需要丢弃但还未接收到
        // 2.数据包总长度为100，可读的字节数为150，说明缓冲区已经包含了整个数据包
        long discard = frameLength - in.readableBytes();
        // 记录一下最大的数据包的长度
        tooLongFrameLength = frameLength;

        if (discard < 0) {
            // buffer contains more bytes then the frameLength so we can discard all now
            // 说明是第二种情况，直接丢弃当前数据包
            in.skipBytes((int) frameLength);
        } else {
            // 说明是第一种情况，还有部分数据未接收到。
            // Enter the discard mode and discard everything received so far.
            // 开启丢弃模式
            discardingTooLongFrame = true;
            // 记录下次还需要丢弃多少字节
            bytesToDiscard = discard;
            // 丢弃缓冲区所有的数据
            in.skipBytes(in.readableBytes());
        }
        // 跟进去
        failIfNecessary(true);
    }

    private static void failOnFrameLengthLessThanInitialBytesToStrip(ByteBuf in,
                                                                     long frameLength,
                                                                     int initialBytesToStrip) {
        in.skipBytes((int) frameLength);
        throw new CorruptedFrameException(
           "Adjusted frame length (" + frameLength + ") is less " +
              "than initialBytesToStrip: " + initialBytesToStrip);
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param   ctx             the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param   in              the {@link ByteBuf} from which to read data
     * @return  frame           the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                          be created.
     */
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (discardingTooLongFrame) {
            // 丢弃模式
            discardingTooLongFrame(in);
        }

        // 判断缓冲区中可读的字节数是否小于长度字节的偏移量
        if (in.readableBytes() < lengthFieldEndOffset) {
            // 说明长度字段的包都还不完整， 半包
            return null;
        }

        // 执行到这里，说明可以解析出长度字段的值了 。
        // 计算出长度字节开始的偏移量
        int actualLengthFieldOffset = in.readerIndex() + lengthFieldOffset;
        // 获取长度字段的值， 不包括lengthAdjustment的调整值
        long frameLength = getUnadjustedFrameLength(in, actualLengthFieldOffset, lengthFieldLength, byteOrder);

        // 如果数据帧长度小于0 ， 说明是错误的数据包
        if (frameLength < 0) {
            // 内部会跳过这个数据包的字节数， 并抛出异常
            failOnNegativeLengthField(in, frameLength, lengthFieldEndOffset);
        }

        // 套用前面的公式，长度字段后的数据字节数  = 长度字段的值 + lengthAdjustment
        // frameLength就是长度字段的值， 加上 lengthAdjustment等于长度字段后的数据字节数
        // lengthFieldEndOffset为lengthFieldOffset+lengthFieldLength
        // 那说明最后计算出的frameLength就是整个数据包的长度
        frameLength += lengthAdjustment + lengthFieldEndOffset;
        // 判断是否为错误的数据包
        if (frameLength < lengthFieldEndOffset) {
            failOnFrameLengthLessThanLengthFieldEndOffset(in, frameLength, lengthFieldEndOffset);
        }
        // 整个数据包的长度是否大于最大帧长度
        // 丢弃模式就是在这里开启的
        // 如果数据包长度大于最大长度
        if (frameLength > maxFrameLength) {
            // 丢弃超出的部分，丢弃模式 。 对超出部分进行处理
            exceededFrameLength(in, frameLength);
            return null;
        }

        // never overflows because it's less than maxFrameLength

        // 执行到这里说明是正常模式
        // 数据包的大小
        int frameLengthInt = (int) frameLength;
        /// 判断缓冲区可读字节数是否小于数据包的字节数
        if (in.readableBytes() < frameLengthInt) {
            // 半包，等于再来解析
            return null;
        }
        // 执行到这里说明缓冲区数据已经包含了数据包
        // 跳过的字节数是否大于数据包的长度
        if (initialBytesToStrip > frameLengthInt) {
            failOnFrameLengthLessThanInitialBytesToStrip(in, frameLength, initialBytesToStrip);
        }
        // 跳过的字节数是否大于数据包的长度
        in.skipBytes(initialBytesToStrip);

        // extract frame
        // 解码
        // 获取当前可读下标
        int readerIndex = in.readerIndex();
        // 获取跳过后的真实数据长度
        int actualFrameLength = frameLengthInt - initialBytesToStrip;
        // 更新一下可读下标
        ByteBuf frame = extractFrame(ctx, in, readerIndex, actualFrameLength);
        // 返回数据
        in.readerIndex(readerIndex + actualFrameLength);
        return frame;
    }

    /**
     * Decodes the specified region of the buffer into an unadjusted frame length.  The default implementation is
     * capable of decoding the specified region into an unsigned 8/16/24/32/64 bit integer.  Override this method to
     * decode the length field encoded differently.  Note that this method must not modify the state of the specified
     * buffer (e.g. {@code readerIndex}, {@code writerIndex}, and the content of the buffer.)
     *
     * @throws DecoderException if failed to decode the specified region
     * 解析长度字段的值
     * offset : 长度字段开始的偏移量
     * length : 长度字节的字节数
     */
    protected long getUnadjustedFrameLength(ByteBuf buf, int offset, int length, ByteOrder order) {
        // 大小端排序
        buf = buf.order(order);
        // 长度字段的值
        long frameLength;
        // 根据长度字段的字节数，获取长度字段的值
        switch (length) {
        case 1:
            // byte
            frameLength = buf.getUnsignedByte(offset);
            break;
        case 2:
            // short
            frameLength = buf.getUnsignedShort(offset);
            break;
        case 3:
            // int 占32位，这里取出后24位，返回int类型
            frameLength = buf.getUnsignedMedium(offset);
            break;
        case 4:
            // int
            frameLength = buf.getUnsignedInt(offset);
            break;
        case 8:
            // long
            frameLength = buf.getLong(offset);
            break;
        default:
            throw new DecoderException(
                    "unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 1, 2, 3, 4, or 8)");
        }
        // 返回长度字段的值
        return frameLength;
    }

    private void failIfNecessary(boolean firstDetectionOfTooLongFrame) {
        if (bytesToDiscard == 0) {
            // Reset to the initial state and tell the handlers that
            // the frame was too large.
            // 说明需要丢弃的数据已经丢弃完成
            // 保存一下被丢弃的数据包的长度
            long tooLongFrameLength = this.tooLongFrameLength;

            this.tooLongFrameLength = 0;
            // 关闭丢弃模式
            discardingTooLongFrame = false;
            // failFast : 默认为true
            // firstDetectionOfTooLongFrame : 传入true
            if (!failFast || firstDetectionOfTooLongFrame) {
                // 快速失败
                fail(tooLongFrameLength);
            }
        } else {
            // 说明还未丢弃完成
            // Keep discarding and notify handlers if necessary.
            if (failFast && firstDetectionOfTooLongFrame) {
                // 快速失败
                fail(tooLongFrameLength);
            }
        }
    }

    /**
     * Extract the sub-region of the specified buffer.
     * <p>
     * If you are sure that the frame and its content are not accessed after
     * the current {@link #decode(ChannelHandlerContext, ByteBuf)}
     * call returns, you can even avoid memory copy by returning the sliced
     * sub-region (i.e. <tt>return buffer.slice(index, length)</tt>).
     * It's often useful when you convert the extracted frame into an object.
     * Refer to the source code of {@link ObjectDecoder} to see how this method
     * is overridden to avoid memory copy.
     */
    // 获取真实的数据
    // index : 可读的下标
    // length : 要读取的长度
    protected ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
        return buffer.retainedSlice(index, length);
    }

    // 程序最终会执行fail()方法并抛出TooLongFrameException异常
    private void fail(long frameLength) {
        // 丢弃完成或未完成都抛出异常
        if (frameLength > 0) {
            throw new TooLongFrameException(
                            "Adjusted frame length exceeds " + maxFrameLength +
                            ": " + frameLength + " - discarded");
        } else {
            throw new TooLongFrameException(
                            "Adjusted frame length exceeds " + maxFrameLength +
                            " - discarding");
        }
    }
}
