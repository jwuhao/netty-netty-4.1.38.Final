/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.protobuf;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

/**
 * A decoder that splits the received {@link ByteBuf}s dynamically by the
 * value of the Google Protocol Buffers
 * <a href="https://developers.google.com/protocol-buffers/docs/encoding#varints">Base
 * 128 Varints</a> integer length field in the message. For example:
 * <pre>
 * BEFORE DECODE (302 bytes)       AFTER DECODE (300 bytes)
 * +--------+---------------+      +---------------+
 * | Length | Protobuf Data |----->| Protobuf Data |
 * | 0xAC02 |  (300 bytes)  |      |  (300 bytes)  |
 * +--------+---------------+      +---------------+
 * </pre>
 *
 * @see CodedInputStream
 * @see CodedInputByteBufferNano
 *
 *
 *
 * ProtobufVarint32FrameDecoder 和 ProtobufVarint32LengthFieldPrepender 是相互对应的， 其作用是，根据数据包中的varint32 中的长度值
 * ，解码一个足额的字节的数组，然后将字节数组交给下一站的解码器ProtobufDecoder
 *
 * 什么时varint32类型的长度呢？为什么不用int 这种固定的长度呢？
 *
 * varint32 是一种紧凑的表示数字的方法，它不是一种具体的数据类型，varint32它用一个或多个字节来表示一个数字，值越小的数字使用越的数字使用越少的
 * 字节数， 值越大使用的字节数越多， varint32 根据值的大小自动进行长度的收缩， 这能减少用保存长度的字节数，也就是说，varint32 与int
 * 类型的最大区别，varint32用一个或多个字节来表示一个数字， varint32 不是固定长度，所以为了更好的减少通信过程中的传输量，消息头中的长度尽量采用
 * varint32格式呢？
 *
 * 至此，Netty 的内置Protobuf 的编码器和解码器已经初步介绍完成了，可以通过这两组编码器和解码器完成Length+ProbotufData （Head -Content）
 * 协议的数据传输，但是，在更加复杂的传输应用场景中，Netty 的内置编码器和解码器是不够用的，例如 ，在Head 部分加上魔数字段进行安全验证
 * 或者还需要对Protobuf Data的内容进行加密和解密等， 也就是说，在复杂的传输应用场景下， 需要定制属于自己的Probobuf 编码器和解码器。
 *
 *
 *
 *
 *
 */
public class ProtobufVarint32FrameDecoder extends ByteToMessageDecoder {

    // TODO maxFrameLength + safe skip + fail-fast option
    //      (just like LengthFieldBasedFrameDecoder)

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        in.markReaderIndex();
        int preIndex = in.readerIndex();
        int length = readRawVarint32(in);
        if (preIndex == in.readerIndex()) {
            return;
        }
        if (length < 0) {
            throw new CorruptedFrameException("negative length: " + length);
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
        } else {
            out.add(in.readRetainedSlice(length));
        }
    }

    /**
     * Reads variable length 32bit int from buffer
     *
     * @return decoded int if buffers readerIndex has been forwarded else nonsense value
     */
    private static int readRawVarint32(ByteBuf buffer) {
        if (!buffer.isReadable()) {
            return 0;
        }
        buffer.markReaderIndex();
        byte tmp = buffer.readByte();
        if (tmp >= 0) {
            return tmp;
        } else {
            int result = tmp & 127;
            if (!buffer.isReadable()) {
                buffer.resetReaderIndex();
                return 0;
            }
            if ((tmp = buffer.readByte()) >= 0) {
                result |= tmp << 7;
            } else {
                result |= (tmp & 127) << 7;
                if (!buffer.isReadable()) {
                    buffer.resetReaderIndex();
                    return 0;
                }
                if ((tmp = buffer.readByte()) >= 0) {
                    result |= tmp << 14;
                } else {
                    result |= (tmp & 127) << 14;
                    if (!buffer.isReadable()) {
                        buffer.resetReaderIndex();
                        return 0;
                    }
                    if ((tmp = buffer.readByte()) >= 0) {
                        result |= tmp << 21;
                    } else {
                        result |= (tmp & 127) << 21;
                        if (!buffer.isReadable()) {
                            buffer.resetReaderIndex();
                            return 0;
                        }
                        result |= (tmp = buffer.readByte()) << 28;
                        if (tmp < 0) {
                            throw new CorruptedFrameException("malformed varint.");
                        }
                    }
                }
            }
            return result;
        }
    }
}
