/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.json;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.channel.ChannelPipeline;

import java.util.List;

/**
 * Splits a byte stream of JSON objects and arrays into individual objects/arrays and passes them up the
 * {@link ChannelPipeline}.
 * <p>
 * The byte stream is expected to be in UTF-8 character encoding or ASCII. The current implementation
 * uses direct {@code byte} to {@code char} cast and then compares that {@code char} to a few low range
 * ASCII characters like {@code '{'}, {@code '['} or {@code '"'}. UTF-8 is not using low range [0..0x7F]
 * byte values for multibyte codepoint representations therefore fully supported by this implementation.
 * <p>
 * This class does not do any real parsing or validation. A sequence of bytes is considered a JSON object/array
 * if it contains a matching number of opening and closing braces/brackets. It's up to a subsequent
 * {@link ChannelHandler} to parse the JSON text into a more usable form i.e. a POJO.
 *
 *  Netty 虽然没有提供了JSON编码器，但提供了一个名为JsonObjectDecoder的解码器， JsonObjectDecoder和普通的JSON解码器又有哪些区别呢？
 *  它们两者虽然都是解码器， 但目标完全不同，前者的目标是将持续传输的字节流切割成一个一个的JSON字节流， 相当于解帧，后者的目标则是将普通的字符串或
 *  字节流直接转换为可用的POJO 对象，以上描述可以很抽象，我们不妨参考一下JsonObjectDecoder的几段关键代码，JsonObjectDecoder 的返回对象如代码清单
 *
 *
 */
public class JsonObjectDecoder extends ByteToMessageDecoder {

    private static final int ST_CORRUPTED = -1;
    private static final int ST_INIT = 0;
    private static final int ST_DECODING_NORMAL = 1;
    private static final int ST_DECODING_ARRAY_STREAM = 2;

    private int openBraces;
    private int idx;

    private int lastReaderIndex;

    private int state;
    private boolean insideString;

    private final int maxObjectLength;
    private final boolean streamArrayElements;

    public JsonObjectDecoder() {
        // 1 MB
        this(1024 * 1024);
    }

    public JsonObjectDecoder(int maxObjectLength) {
        this(maxObjectLength, false);
    }

    public JsonObjectDecoder(boolean streamArrayElements) {
        this(1024 * 1024, streamArrayElements);
    }

    /**
     * @param maxObjectLength   maximum number of bytes a JSON object/array may use (including braces and all).
     *                             Objects exceeding this length are dropped and an {@link TooLongFrameException}
     *                             is thrown.
     * @param streamArrayElements   if set to true and the "top level" JSON object is an array, each of its entries
     *                                  is passed through the pipeline individually and immediately after it was fully
     *                                  received, allowing for arrays with "infinitely" many elements.
     *
     */
    public JsonObjectDecoder(int maxObjectLength, boolean streamArrayElements) {
        if (maxObjectLength < 1) {
            throw new IllegalArgumentException("maxObjectLength must be a positive int");
        }
        this.maxObjectLength = maxObjectLength;
        this.streamArrayElements = streamArrayElements;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (state == ST_CORRUPTED) {
            in.skipBytes(in.readableBytes());
            return;
        }

        if (this.idx > in.readerIndex() && lastReaderIndex != in.readerIndex()) {
            this.idx = in.readerIndex() + (idx - lastReaderIndex);
        }

        // index of next byte to process.
        int idx = this.idx;
        int wrtIdx = in.writerIndex();

        if (wrtIdx > maxObjectLength) {
            // buffer size exceeded maxObjectLength; discarding the complete buffer.
            in.skipBytes(in.readableBytes());
            reset();
            throw new TooLongFrameException(
                            "object length exceeds " + maxObjectLength + ": " + wrtIdx + " bytes discarded");
        }

        for (/* use current idx */; idx < wrtIdx; idx++) {
            byte c = in.getByte(idx);
            if (state == ST_DECODING_NORMAL) {
                decodeByte(c, in, idx);

                // All opening braces/brackets have been closed. That's enough to conclude
                // that the JSON object/array is complete.

                if (openBraces == 0) {
                    // 当openBraces为0 ， 说明 { 和 } 已经完全成对出现，JSON 对象或数组可以解析出了
                    // 当 { 和 } 还没有出现时，调用decodeByte来控制openBrances 数量
                    ByteBuf json = extractObject(ctx, in, in.readerIndex(), idx + 1 - in.readerIndex());
                    if (json != null) {
                        out.add(json);
                    }

                    // The JSON object/array was extracted => discard the bytes from
                    // the input buffer.
                    in.readerIndex(idx + 1);
                    // Reset the object state to get ready for the next JSON object/text
                    // coming along the byte stream.
                    reset();
                }
            } else if (state == ST_DECODING_ARRAY_STREAM) {
                decodeByte(c, in, idx);

                if (!insideString && (openBraces == 1 && c == ',' || openBraces == 0 && c == ']')) {
                    // skip leading spaces. No range check is needed and the loop will terminate
                    // because the byte at position idx is not a whitespace.
                    for (int i = in.readerIndex(); Character.isWhitespace(in.getByte(i)); i++) {
                        in.skipBytes(1);
                    }

                    // skip trailing spaces.
                    int idxNoSpaces = idx - 1;
                    while (idxNoSpaces >= in.readerIndex() && Character.isWhitespace(in.getByte(idxNoSpaces))) {
                        idxNoSpaces--;
                    }

                    ByteBuf json = extractObject(ctx, in, in.readerIndex(), idxNoSpaces + 1 - in.readerIndex());
                    if (json != null) {
                        out.add(json);
                    }

                    in.readerIndex(idx + 1);

                    if (c == ']') {
                        reset();
                    }
                }
            // JSON object/array detected. Accumulate bytes until all braces/brackets are closed.
            } else if (c == '{' || c == '[') {
                initDecoding(c);

                if (state == ST_DECODING_ARRAY_STREAM) {
                    // Discard the array bracket
                    in.skipBytes(1);
                }
            // Discard leading spaces in front of a JSON object/array.
            } else if (Character.isWhitespace(c)) {
                in.skipBytes(1);
            } else {
                state = ST_CORRUPTED;
                throw new CorruptedFrameException(
                        "invalid JSON received at byte position " + idx + ": " + ByteBufUtil.hexDump(in));
            }
        }

        if (in.readableBytes() == 0) {
            this.idx = 0;
        } else {
            this.idx = idx;
        }
        this.lastReaderIndex = in.readerIndex();
    }

    /**
     * Override this method if you want to filter the json objects/arrays that get passed through the pipeline.
     */
    @SuppressWarnings("UnusedParameters")
    // JsonObjectDecoder的返回对象
    protected ByteBuf extractObject(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
        return buffer.retainedSlice(index, length);
    }
    // 上述代码表明，我们最终解析出来的并不是POJO ,而是字节数组ByteBuf ，要进行转换，还需要做进一步的工作，但是，JsonObjectDecoder 返回无疑已经是完整的且可以解析的
    // JSON，既然是解帧， 那么JsonObjectDecoder 具体是如何做到的呢？是根据长度字段还是固定字符进行分割的呢？ 既然是解帧，那么JsonObjectDecoder具体是如何做到的呢？
    // 根据长度字段还是固定字符进行分割，实际上都不是，JsonObjectDecoder 是根据 { 和 } 是否已经完全成对出现，是否已经完全成对的出现来判断JSON字节数组的完整性，
    // 例如 { "tableId":102 就不是完整的了， 再如{"TableId:102,"nestObject":{"name","myName"} 也不是完整的，因为{和}没有成对出现 。



    // 控制openBraces变化的decodeByte方法的实现
    private void decodeByte(byte c, ByteBuf in, int idx) {
        if ((c == '{' || c == '[') && !insideString) {
            openBraces++;
        } else if ((c == '}' || c == ']') && !insideString) {
            openBraces--;
        } else if (c == '"') {
            // start of a new JSON string. It's necessary to detect strings as they may
            // also contain braces/brackets and that could lead to incorrect results.
            if (!insideString) {
                insideString = true;
            } else {
                int backslashCount = 0;
                idx--;
                while (idx >= 0) {
                    if (in.getByte(idx) == '\\') {
                        backslashCount++;
                        idx--;
                    } else {
                        break;
                    }
                }
                // The double quote isn't escaped only if there are even "\"s.
                if (backslashCount % 2 == 0) {
                    // Since the double quote isn't escaped then this is the end of a string.
                    insideString = false;
                }
            }
        }
    }

    private void initDecoding(byte openingBrace) {
        openBraces = 1;
        if (openingBrace == '[' && streamArrayElements) {
            state = ST_DECODING_ARRAY_STREAM;
        } else {
            state = ST_DECODING_NORMAL;
        }
    }

    private void reset() {
        insideString = false;
        state = ST_INIT;
        openBraces = 0;
    }
}
