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
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * A decoder that splits the received {@link ByteBuf}s by one or more
 * delimiters.  It is particularly useful for decoding the frames which ends
 * with a delimiter such as {@link Delimiters#nulDelimiter() NUL} or
 * {@linkplain Delimiters#lineDelimiter() newline characters}.
 *
 * <h3>Predefined delimiters</h3>
 * <p>
 * {@link Delimiters} defines frequently used delimiters for convenience' sake.
 *
 * <h3>Specifying more than one delimiter</h3>
 * <p>
 * {@link DelimiterBasedFrameDecoder} allows you to specify more than one
 * delimiter.  If more than one delimiter is found in the buffer, it chooses
 * the delimiter which produces the shortest frame.  For example, if you have
 * the following data in the buffer:
 * <pre>
 * +--------------+
 * | ABC\nDEF\r\n |
 * +--------------+
 * </pre>
 * a {@link DelimiterBasedFrameDecoder}({@link Delimiters#lineDelimiter() Delimiters.lineDelimiter()})
 * will choose {@code '\n'} as the first delimiter and produce two frames:
 * <pre>
 * +-----+-----+
 * | ABC | DEF |
 * +-----+-----+
 * </pre>
 * rather than incorrectly choosing {@code '\r\n'} as the first delimiter:
 * <pre>
 * +----------+
 * | ABC\nDEF |
 * +----------+
 * </pre>
 * 找到ByteBuf中是否有对应的特殊字 符，若有，则截断读取对应的消息;
 *
 *
 * 自定义分隔符数据包解码器
 * DelimiterBasedFrameDecoder是LineBasedFrameDecoder按照行分割的通用版本，不同之外在于，这个解码器更加的灵活 ， 可以自己定义分隔符
 * , 而不是局限于换行符， 如果使用这个解码器，那么接收到的数据包，末尾 必须带上对应的分隔符
 *
 *
 * 不仅可以使用换行符，还可以将其他的特殊字符作为数据包的分隔符， 例如制表符"\t", 其构造方法如下
 *
 *
 *
 */
//自定义分隔符解码器
public class DelimiterBasedFrameDecoder extends ByteToMessageDecoder {

    private final ByteBuf[] delimiters; //自定义多个分隔符，可以使用多个
    private final int maxFrameLength;       // 每个消息段落的最大长度
    private final boolean stripDelimiter;       // 解码消息时，是否丢弃分隔符
    private final boolean failFast;         // 遇到错误时，是否抛出异常
    private boolean discardingTooLongFrame;         // 状态变量，是否正在丢弃一个段的消息
    private int tooLongFrameLength;             //丢弃总长度
    /** Set only when decoding with "\n" and "\r\n" as the delimiter.  */
    private final LineBasedFrameDecoder lineBasedDecoder;                   // \r \n 的解码器

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param delimiter  the delimiter
     */
    public DelimiterBasedFrameDecoder(int maxFrameLength, ByteBuf delimiter) {
        this(maxFrameLength, true, delimiter);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param delimiter  the delimiter
     * maxFrameLength:解码数据包的最大长度
     * stripDelimiter:解码后数据包是否去掉分隔符，一般选择是
     * delimiter： 分隔符
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, ByteBuf delimiter) {
        this(maxFrameLength, stripDelimiter, true, delimiter);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param failFast  If <tt>true</tt>, a {@link TooLongFrameException} is
     *                  thrown as soon as the decoder notices the length of the
     *                  frame will exceed <tt>maxFrameLength</tt> regardless of
     *                  whether the entire frame has been read.
     *                  If <tt>false</tt>, a {@link TooLongFrameException} is
     *                  thrown after the entire frame that exceeds
     *                  <tt>maxFrameLength</tt> has been read.
     * @param delimiter  the delimiter
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, boolean failFast,
            ByteBuf delimiter) {
        this(maxFrameLength, stripDelimiter, failFast, new ByteBuf[] {
                delimiter.slice(delimiter.readerIndex(), delimiter.readableBytes())});
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param delimiters  the delimiters
     */
    public DelimiterBasedFrameDecoder(int maxFrameLength, ByteBuf... delimiters) {
        this(maxFrameLength, true, delimiters);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param delimiters  the delimiters
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, ByteBuf... delimiters) {
        this(maxFrameLength, stripDelimiter, true, delimiters);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param failFast  If <tt>true</tt>, a {@link TooLongFrameException} is
     *                  thrown as soon as the decoder notices the length of the
     *                  frame will exceed <tt>maxFrameLength</tt> regardless of
     *                  whether the entire frame has been read.
     *                  If <tt>false</tt>, a {@link TooLongFrameException} is
     *                  thrown after the entire frame that exceeds
     *                  <tt>maxFrameLength</tt> has been read.
     * @param delimiters  the delimiters
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, boolean failFast, ByteBuf... delimiters) {
        validateMaxFrameLength(maxFrameLength);
        if (delimiters == null) {
            throw new NullPointerException("delimiters");
        }
        if (delimiters.length == 0) {
            throw new IllegalArgumentException("empty delimiters");
        }
        // delimiters里的分隔符是否是 \r \n
        if (isLineBased(delimiters) && !isSubclass()) {
            lineBasedDecoder = new LineBasedFrameDecoder(maxFrameLength, stripDelimiter, failFast);
            this.delimiters = null;
        } else {
            // 创建数组
            this.delimiters = new ByteBuf[delimiters.length];
            for (int i = 0; i < delimiters.length; i ++) {
                ByteBuf d = delimiters[i];
                validateDelimiter(d);
                // 创建d的分区放入数组
                this.delimiters[i] = d.slice(d.readerIndex(), d.readableBytes());
            }
            lineBasedDecoder = null;
        }
        this.maxFrameLength = maxFrameLength;
        this.stripDelimiter = stripDelimiter;
        this.failFast = failFast;
    }

    /** Returns true if the delimiters are "\n" and "\r\n".  */
    // 判断是否是以 \r \n 分割
    private static boolean isLineBased(final ByteBuf[] delimiters) {
        if (delimiters.length != 2) {
            return false;
        }
        ByteBuf a = delimiters[0];
        ByteBuf b = delimiters[1];
        if (a.capacity() < b.capacity()) {
            a = delimiters[1];
            b = delimiters[0];
        }
        return a.capacity() == 2 && b.capacity() == 1
                && a.getByte(0) == '\r' && a.getByte(1) == '\n'
                && b.getByte(0) == '\n';
    }

    /**
     * Return {@code true} if the current instance is a subclass of DelimiterBasedFrameDecoder
     */

    // 判断是否是子类
    private boolean isSubclass() {
        return getClass() != DelimiterBasedFrameDecoder.class;
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param   ctx             the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param   buffer          the {@link ByteBuf} from which to read data
     * @return  frame           the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                          be created.
     */
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        // 如果分割符是以\r\n，则用lineBasedDecoder解码器解析
        if (lineBasedDecoder != null) {
            // 此类相当于lineBasedDecoder
            return lineBasedDecoder.decode(ctx, buffer);
        }
        // Try all delimiters and choose the delimiter which yields the shortest frame.
        int minFrameLength = Integer.MAX_VALUE;
        ByteBuf minDelim = null;
        // 循环所有的分隔符， 挨个去匹配
        for (ByteBuf delim: delimiters) {
            // 匹配分隔符的起始位置 oinh
            int frameLength = indexOf(buffer, delim);
            // 大于0 ， 并且小于int最大值，说明匹配成功
            if (frameLength >= 0 && frameLength < minFrameLength) {
                minFrameLength = frameLength;
                minDelim = delim;
            }
        }
        // 如果匹配成功
        if (minDelim != null) {
            // 分隔符的capacity 就代表了分配符的长度
            int minDelimLength = minDelim.capacity();
            ByteBuf frame;
            // 如果之前已经开始丢弃字节
            if (discardingTooLongFrame) {
                // We've just finished discarding a very large frame.
                // Go back to the initial state.
                // 恢复标记位
                discardingTooLongFrame = false;
                // 跳过minFrameLength + 分隔符的长度，表示丢弃了前一个完整的消息
                buffer.skipBytes(minFrameLength + minDelimLength);
                int tooLongFrameLength = this.tooLongFrameLength;
                // 丢弃的字节总数
                this.tooLongFrameLength = 0;
                if (!failFast) {
                    fail(tooLongFrameLength);
                }
                return null;
            }
            // 消息的长度大于阈值
            if (minFrameLength > maxFrameLength) {
                // Discard read frame.
                // 丢弃一个消息
                buffer.skipBytes(minFrameLength + minDelimLength);
                fail(minFrameLength);
                return null;
            }

            // 丢弃分隔符
            if (stripDelimiter) {
                // 返回一个消息的frame
                frame = buffer.readRetainedSlice(minFrameLength);
                // 丢弃分隔符
                buffer.skipBytes(minDelimLength);
            } else {
                // 返回一个消息，包括了分隔符
                frame = buffer.readRetainedSlice(minFrameLength + minDelimLength);
            }

            return frame;
        } else {                        // 没找到分隔符的位置
            // 正常情况
            if (!discardingTooLongFrame) {
                // 如果可读字节大于最大长度限制
                if (buffer.readableBytes() > maxFrameLength) {
                    // Discard the content of the buffer until a delimiter is found.
                    tooLongFrameLength = buffer.readableBytes();
                    // 丢弃字节
                    buffer.skipBytes(buffer.readableBytes());
                    // 标记丢弃的状态
                    discardingTooLongFrame = true;
                    if (failFast) {
                        fail(tooLongFrameLength);
                    }
                }
            } else {
                // 之前discardingTooLongFrame已经设置为true
                // Still discarding the buffer since a delimiter is not found.
                // 增加总丢弃的数量
                tooLongFrameLength += buffer.readableBytes();
                // 继续丢弃字节
                buffer.skipBytes(buffer.readableBytes());
            }
            return null;
        }
    }

    private void fail(long frameLength) {
        if (frameLength > 0) {
            throw new TooLongFrameException(
                            "frame length exceeds " + maxFrameLength +
                            ": " + frameLength + " - discarded");
        } else {
            throw new TooLongFrameException(
                            "frame length exceeds " + maxFrameLength +
                            " - discarding");
        }
    }

    /**
     * Returns the number of bytes between the readerIndex of the haystack and
     * the first needle found in the haystack.  -1 is returned if no needle is
     * found in the haystack.
     */
    private static int indexOf(ByteBuf haystack, ByteBuf needle) {
        // 循环输入的字节流缓冲区，假设为 a b c d e
        for (int i = haystack.readerIndex(); i < haystack.writerIndex(); i ++) {
            // 从a[0]位置开始找
            int haystackIndex = i;
            int needleIndex;
            // 循环分隔符
            for (needleIndex = 0; needleIndex < needle.capacity(); needleIndex ++) {
                // 如果分隔符与haystack.getByte(haystackIndex) 不同，则结束当前循环
                // haystack.getByte(haystackIndex) 从b开始继续查询
                if (haystack.getByte(haystackIndex) != needle.getByte(needleIndex)) {
                    break;
                } else {
                    haystackIndex ++;
                    // 依次拿出bcde进行匹配，如果输出的缓冲区达到了末尾 ， 并且分隔符缓冲区还没有达到末尾，说明匹配失败
                    if (haystackIndex == haystack.writerIndex() &&
                        needleIndex != needle.capacity() - 1) {
                        return -1;
                    }
                }
            }

            // 如果分隔符缓冲区达到末尾 ，还没有发生上面的情况，说明匹配到了
            if (needleIndex == needle.capacity()) {
                // Found the needle from the haystack!
                // i 减去haystack.readerIndex()的位置就是分隔符的位置
                return i - haystack.readerIndex();
            }
        }
        return -1;
    }

    private static void validateDelimiter(ByteBuf delimiter) {
        if (delimiter == null) {
            throw new NullPointerException("delimiter");
        }
        if (!delimiter.isReadable()) {
            throw new IllegalArgumentException("empty delimiter");
        }
    }

    private static void validateMaxFrameLength(int maxFrameLength) {
        checkPositive(maxFrameLength, "maxFrameLength");
    }
}
