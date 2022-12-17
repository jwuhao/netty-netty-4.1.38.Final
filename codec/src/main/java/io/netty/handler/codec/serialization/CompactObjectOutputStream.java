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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;

class CompactObjectOutputStream extends ObjectOutputStream {

    static final int TYPE_FAT_DESCRIPTOR = 0;
    static final int TYPE_THIN_DESCRIPTOR = 1;

    CompactObjectOutputStream(OutputStream out) throws IOException {
        super(out);
    }

    @Override
    protected void writeStreamHeader() throws IOException {
        // 相比 JDK 少了writeShort（STAREAM_MAGIC ）
        writeByte(STREAM_VERSION);
    }

    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
        Class<?> clazz = desc.forClass();
        if (clazz.isPrimitive() || clazz.isArray() || clazz.isInterface() ||
            desc.getSerialVersionUID() == 0) {
            // 相比JDK 少了很多的信息，比如元信息
            write(TYPE_FAT_DESCRIPTOR);
            super.writeClassDescriptor(desc);
        } else {
            write(TYPE_THIN_DESCRIPTOR);
            // 但是这也需要类的名称，类的名称在进行反序列化（反射时就会用到）因而很重要
            writeUTF(desc.getName());
            /**
             * JDK 的序列化则多了如下信息
             * out.writeShort(fields.length);
             * for(int i = 0 ;i < fields.length;i ++){
             *      ObjectStreamField f = fields[i];
             *      out.writeByte(f.getTypeCode());
             *      out.writeUTF(f.getName());
             *      if( ! f.isPrimitive()){
             *          out.writeTypeString(f.getTypeString());
             *      }
             * }
             * 少了类的元信息， 如何进行反序列化？ 实际上，我们已经存储了类的名称，因此，当接收到数据之后，就可以直接通过反射得到对应的信息
             * 通过这种方式，Netty 的ObjectEncoder大大减少了需要传输的数据量
             *
             *
             */
        }
    }
}
