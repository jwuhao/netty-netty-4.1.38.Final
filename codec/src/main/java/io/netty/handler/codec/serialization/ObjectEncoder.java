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
package io.netty.handler.codec.serialization;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * An encoder which serializes a Java object into a {@link ByteBuf}.
 * <p>
 * Please note that the serialized form this encoder produces is not
 * compatible with the standard {@link ObjectInputStream}.  Please use
 * {@link ObjectDecoder} or {@link ObjectDecoderInputStream} to ensure the
 * interoperability with this encoder.
 *
 * Netty 的ObjectEncoder 能完成更多的工作
 * Netty的ObjectEncoder 不仅能够完成数据的序列化工作，还能完成封帧操作，关于封帧
 *
 *
 *
 *

 *
 */
@Sharable
public class ObjectEncoder extends MessageToByteEncoder<Serializable> {
    private static final byte[] LENGTH_PLACEHOLDER = new byte[4];

    @Override
    protected void encode(ChannelHandlerContext ctx, Serializable msg, ByteBuf out) throws Exception {
        int startIdx = out.writerIndex();

        ByteBufOutputStream bout = new ByteBufOutputStream(out);
        ObjectOutputStream oout = null;
        try {
            // 预留4个字节长度
            bout.write(LENGTH_PLACEHOLDER);
            oout = new CompactObjectOutputStream(bout);
            // 序列化对象
            oout.writeObject(msg);
            oout.flush();
        } finally {
            if (oout != null) {
                oout.close();
            } else {
                bout.close();
            }
        }

        int endIdx = out.writerIndex();
        // 设置长度字段
        out.setInt(startIdx, endIdx - startIdx - 4);
    }
}
