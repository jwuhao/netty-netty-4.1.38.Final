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
package io.netty.buffer;

import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

import static io.netty.util.internal.MathUtil.isOutOfBounds;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * A skeletal implementation of a buffer.
 *
 * AbstractByteBuf是ByteBuf的子类，它定义了一些公共属性，如 读索引、写索引、mark、最大容量等。AbstractByteBuf实现了一套
 * 读/写操作的模板方法，其缓冲区真正的数据读/写由其子类完成。图 4-7展示了AbstractByteBuf的核心功能，接下来对其核心功的源码进 行剖析。
 *
 *                                  |------> 读/写索引
 *                          |------>|------> 标记读/写索引
 *                          |        |------>最大容量
 *                          |
 *                          |               |------> writeBytes
 *                          |------> 写---->|------> ensureWritable
 *                          |               |------> setBytes
 *                          |
 *                          |               |------>readBytes
 *   AbstractByteBuf------> |------> 读---->|------>getBytes
 *                          |
 *                          |
 *                          |------> 丢弃已读数据--- |---------->discardReadBytes
 *                                                  |----------> adjustMarkers
 *
 */
public abstract class AbstractByteBuf extends ByteBuf {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractByteBuf.class);
    private static final String LEGACY_PROP_CHECK_ACCESSIBLE = "io.netty.buffer.bytebuf.checkAccessible";
    private static final String PROP_CHECK_ACCESSIBLE = "io.netty.buffer.checkAccessible";
    static final boolean checkAccessible; // accessed from CompositeByteBuf
    private static final String PROP_CHECK_BOUNDS = "io.netty.buffer.checkBounds";
    private static final boolean checkBounds;

    static {
        if (SystemPropertyUtil.contains(PROP_CHECK_ACCESSIBLE)) {
            checkAccessible = SystemPropertyUtil.getBoolean(PROP_CHECK_ACCESSIBLE, true);
        } else {
            checkAccessible = SystemPropertyUtil.getBoolean(LEGACY_PROP_CHECK_ACCESSIBLE, true);
        }
        checkBounds = SystemPropertyUtil.getBoolean(PROP_CHECK_BOUNDS, true);
        if (logger.isDebugEnabled()) {
            logger.debug("-D{}: {}", PROP_CHECK_ACCESSIBLE, checkAccessible);
            logger.debug("-D{}: {}", PROP_CHECK_BOUNDS, checkBounds);
        }
    }

    static final ResourceLeakDetector<ByteBuf> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(ByteBuf.class);

    int readerIndex;                //读索引
    int writerIndex;                // 写索引
    /**
     * 标记读索引
     * 解码时，由于消息不完整，无法处理
     * 需要将readerIndex复位
     * 此时需要先为索引做个标记
     *
     * 暂存的读指针
     */
    private int markedReaderIndex;
    // 标记写索引
    // 暂存的写指针
    private int markedWriterIndex;
    // 最大容量
    private int maxCapacity;

    protected AbstractByteBuf(int maxCapacity) {
        checkPositiveOrZero(maxCapacity, "maxCapacity");
        this.maxCapacity = maxCapacity;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ByteBuf asReadOnly() {
        if (isReadOnly()) {
            return this;
        }
        return Unpooled.unmodifiableBuffer(this);
    }

    @Override
    public int maxCapacity() {
        return maxCapacity;
    }

    protected final void maxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    @Override
    public int readerIndex() {
        return readerIndex;
    }

    private static void checkIndexBounds(final int readerIndex, final int writerIndex, final int capacity) {
        if (readerIndex < 0 || readerIndex > writerIndex || writerIndex > capacity) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex: %d, writerIndex: %d (expected: 0 <= readerIndex <= writerIndex <= capacity(%d))",
                    readerIndex, writerIndex, capacity));
        }
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }
        this.readerIndex = readerIndex;
        return this;
    }

    @Override
    public int writerIndex() {
        return writerIndex;
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }
        this.writerIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }
        setIndex0(readerIndex, writerIndex);
        return this;
    }

    @Override
    public ByteBuf clear() {
        readerIndex = writerIndex = 0;
        return this;
    }

    @Override
    public boolean isReadable() {
        return writerIndex > readerIndex;
    }

    @Override
    public boolean isReadable(int numBytes) {
        return writerIndex - readerIndex >= numBytes;
    }

    @Override
    public boolean isWritable() {
        return capacity() > writerIndex;
    }

    @Override
    public boolean isWritable(int numBytes) {
        return capacity() - writerIndex >= numBytes;
    }

    @Override
    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    @Override
    public int writableBytes() {
        return capacity() - writerIndex;
    }

    @Override
    public int maxWritableBytes() {
        return maxCapacity() - writerIndex;
    }

    @Override
    public ByteBuf markReaderIndex() {
        markedReaderIndex = readerIndex;
        return this;
    }

    @Override
    public ByteBuf resetReaderIndex() {
        readerIndex(markedReaderIndex);
        return this;
    }

    @Override
    public ByteBuf markWriterIndex() {
        markedWriterIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf resetWriterIndex() {
        writerIndex(markedWriterIndex);
        return this;
    }

    @Override
    public ByteBuf discardReadBytes() {
        ensureAccessible();
        if (readerIndex == 0) {
            return this;
        }

        if (readerIndex != writerIndex) {
            setBytes(0, this, readerIndex, writerIndex - readerIndex);
            writerIndex -= readerIndex;
            adjustMarkers(readerIndex);
            readerIndex = 0;
        } else {
            adjustMarkers(readerIndex);
            writerIndex = readerIndex = 0;
        }
        return this;
    }

    @Override
    public ByteBuf discardSomeReadBytes() {
        ensureAccessible();
        if (readerIndex == 0) {
            return this;
        }

        if (readerIndex == writerIndex) {
            adjustMarkers(readerIndex);
            writerIndex = readerIndex = 0;
            return this;
        }

        if (readerIndex >= capacity() >>> 1) {
            setBytes(0, this, readerIndex, writerIndex - readerIndex);
            writerIndex -= readerIndex;
            adjustMarkers(readerIndex);
            readerIndex = 0;
        }
        return this;
    }
    // 当对缓存进行读操作，由于某种原因，可能需要对之前的操作进行回滚。ByteBuf提供了：
    // a，markReaderIndex：将当前的readerIndex备份到markedReaderIndex中；
    // b，resetReaderIndex：将当前的readerIndex设置为markedReaderIndex；
    // c，markWriterIndex：将当前的writerIndex备份到markedWriterIndex；
    // d，resetWriterIndex：将当前的writerIndex设置为markedWriterIndex。
    protected final void adjustMarkers(int decrement) {
        int markedReaderIndex = this.markedReaderIndex;
        if (markedReaderIndex <= decrement) {
            this.markedReaderIndex = 0;
            int markedWriterIndex = this.markedWriterIndex;
            if (markedWriterIndex <= decrement) {
                this.markedWriterIndex = 0;
            } else {
                this.markedWriterIndex = markedWriterIndex - decrement;
            }
        } else {
            this.markedReaderIndex = markedReaderIndex - decrement;
            markedWriterIndex -= decrement;
        }
    }

    @Override
    public ByteBuf ensureWritable(int minWritableBytes) {
        checkPositiveOrZero(minWritableBytes, "minWritableBytes");
        ensureWritable0(minWritableBytes);
        return this;
    }

    final void ensureWritable0(int minWritableBytes) {
        // 获取ByteBuf对象的引用计数, 如果返回值为零，则说明该对象被销毁，会抛出异常
        ensureAccessible();
        // 若可写字节数大于 minWritableBytes,则无须扩容
        if (minWritableBytes <= writableBytes()) {
            return;
        }
        // 获取写索引
        final int writerIndex = writerIndex();
        /**
         *  checkBounds
         *  判断将要写入的字节数是否大于最大可写字节数（maxCapacity - writerIndex）
         *  如果大于则直接抛出异常， 否则继续执行
         */
        if (checkBounds) {
            if (minWritableBytes > maxCapacity - writerIndex) {
                throw new IndexOutOfBoundsException(String.format(
                        "writerIndex(%d) + minWritableBytes(%d) exceeds maxCapacity(%d): %s",
                        writerIndex, minWritableBytes, maxCapacity, this));
            }
        }

        // Normalize the current capacity to the power of 2.
        // 最小容量
        int minNewCapacity = writerIndex + minWritableBytes;
        // 计算自动扩容后的容量，需要满足最小容量，必须是2的幂数
        int newCapacity = alloc().calculateNewCapacity(minNewCapacity, maxCapacity);

        /**
         * maxFastWritableBytes 返回不用复制和重新分配内存的最快， 最大可写字节数，默认等于writeableBytes
         */
        int fastCapacity = writerIndex + maxFastWritableBytes();
        // 减少重新分配内存
        // Grow by a smaller amount if it will avoid reallocation
        if (newCapacity > fastCapacity && minNewCapacity <= fastCapacity) {
            newCapacity = fastCapacity;
        }

        // Adjust to the new capacity.
        // 由子类将容量调整到新容量值
        capacity(newCapacity);
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        ensureAccessible();
        checkPositiveOrZero(minWritableBytes, "minWritableBytes");

        if (minWritableBytes <= writableBytes()) {
            return 0;
        }

        final int maxCapacity = maxCapacity();
        final int writerIndex = writerIndex();
        if (minWritableBytes > maxCapacity - writerIndex) {
            if (!force || capacity() == maxCapacity) {
                return 1;
            }

            capacity(maxCapacity);
            return 3;
        }

        // Normalize the current capacity to the power of 2.
        int minNewCapacity = writerIndex + minWritableBytes;
        int newCapacity = alloc().calculateNewCapacity(minNewCapacity, maxCapacity);

        int fastCapacity = writerIndex + maxFastWritableBytes();
        // Grow by a smaller amount if it will avoid reallocation
        if (newCapacity > fastCapacity && minNewCapacity <= fastCapacity) {
            newCapacity = fastCapacity;
        }

        // Adjust to the new capacity.
        capacity(newCapacity);
        return 2;
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        if (endianness == order()) {
            return this;
        }
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }
        return newSwappedByteBuf();
    }

    /**
     * Creates a new {@link SwappedByteBuf} for this {@link ByteBuf} instance.
     */
    protected SwappedByteBuf newSwappedByteBuf() {
        return new SwappedByteBuf(this);
    }

    @Override
    public byte getByte(int index) {     // 获取此缓冲区中位于指定绝对索引处的字节
        checkIndex(index);
        return _getByte(index);
    }

    protected abstract byte _getByte(int index);

    @Override
    public boolean getBoolean(int index) { // 获取该缓冲区中指定绝对索引处的boolean值
        return getByte(index) != 0;
    }

    @Override
    public short getUnsignedByte(int index) {       // 获取该缓冲区中指定的绝对索引处的无符号字节，注： 返回值为short类型。
        return (short) (getByte(index) & 0xFF);
    }

    @Override
    public short getShort(int index) {          // 获取此缓冲区中指定绝对索引处的16倍短整数
        checkIndex(index, 2);
        return _getShort(index);
    }

    protected abstract short _getShort(int index);

    @Override
    public short getShortLE(int index) {       // 获取该缓冲区中指定的绝对索引处的16位无符号短整数
        checkIndex(index, 2);
        return _getShortLE(index);
    }

    protected abstract short _getShortLE(int index);

    @Override
    public int getUnsignedShort(int index) {       // 获取此缓冲区中指定绝对索引处的16位无符号短整数，注：返回的是int类型
        return getShort(index) & 0xFFFF;
    }

    @Override
    public int getUnsignedShortLE(int index) {  // 获取该缓冲区中以小字节顺序指定的绝对索引处无符号16位短整数，注，返回值为int类型
        return getShortLE(index) & 0xFFFF;
    }

    @Override
    public int getUnsignedMedium(int index) {   // 获取该缓冲区中以小端字节顺序指定的绝对索引处的无符号24位中等整数 。
        checkIndex(index, 3);
        return _getUnsignedMedium(index);
    }

    protected abstract int _getUnsignedMedium(int index);

    @Override
    public int getUnsignedMediumLE(int index) {     // 获取该缓冲区中以小端字节顺序指定的绝对索引处的无符号24位中等整数
        checkIndex(index, 3);
        return _getUnsignedMediumLE(index);
    }

    protected abstract int _getUnsignedMediumLE(int index);

    @Override
    public int getMedium(int index) {
        int value = getUnsignedMedium(index);
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public int getMediumLE(int index) {
        int value = getUnsignedMediumLE(index);
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public int getInt(int index) {              // 获取此缓冲区中指定绝对索引处的32位整数
        checkIndex(index, 4);
        return _getInt(index);
    }

    protected abstract int _getInt(int index);

    @Override
    public int getIntLE(int index) {            // 获取此缓冲区中具有小端字节顺序的指定绝对索引处的32位整数
        checkIndex(index, 4);
        return _getIntLE(index);
    }

    protected abstract int _getIntLE(int index);

    @Override
    public long getUnsignedInt(int index) {     // 获取此缓冲区中指定绝对索引处的无符号32位整数，注：返回值为long类型值
        return getInt(index) & 0xFFFFFFFFL;
    }

    @Override
    public long getUnsignedIntLE(int index) {       // 获取此缓冲区中以小端字节顺序指定绝对索引处的无符号32位整数
        return getIntLE(index) & 0xFFFFFFFFL;
    }

    @Override
    public long getLong(int index) {    // 获取此缓冲区中指定绝对索引处的64位的长整数
        checkIndex(index, 8);
        return _getLong(index);
    }

    protected abstract long _getLong(int index);

    @Override
    public long getLongLE(int index) {  // 以小端字节顺序在此缓冲区中指定绝对索引处获取一个64位长整数
        checkIndex(index, 8);
        return _getLongLE(index);
    }

    protected abstract long _getLongLE(int index);

    @Override
    public char getChar(int index) {            // 获取此缓冲区中指定的绝对索引入的2个字节UTF-16字符
        return (char) getShort(index);
    }

    @Override
    public float getFloat(int index) {                  // 获取此缓冲区中指定的绝对索引处的32位浮点数
        return Float.intBitsToFloat(getInt(index));
    }

    @Override
    public double getDouble(int index) {                // 获取此缓冲区中指定绝对索引处的64位浮点数
        return Double.longBitsToDouble(getLong(index));
    }

    @Override
    //  getBytes(int index, byte[] dst) 方法来实现。 传递的length大小就是目标缓冲区dst的剩下可写区域
    // Writeable bytes 的大小

    public ByteBuf getBytes(int index, byte[] dst) {
        getBytes(index, dst, 0, dst.length);
        return this;
    }

    @Override
    // 方法也调用了ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) 方法，传递了dstIndex 就是目标缓存区
    public ByteBuf getBytes(int index, ByteBuf dst) {
        getBytes(index, dst, dst.writableBytes());
        return this;
    }

    @Override
    // getBytes() 方法因此我们可以得出
    // 首先这三个方法都不会改变该缓冲区的读索引readerIndex或者写索引writerIndex的值 。
    // 前两个方法会改变目标缓存区dst的写索引writerIndex值，而第三个方法则不会
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        getBytes(index, dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }
    // 这是因为前两个方法是将该缓冲区的数据从目标缓冲区dst当前写引用
    // writerIndex位置处开始写入， 因此传输完成后，可以更改目标缓冲区dst的写引用writerIndex
    // 而第三个方法则是从目标缓冲区dst任意位置处开始写，就不好直接改变写引用writerIndex




    @Override   // 获取在给定索引处具有的给定长度CharSequence
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        if (CharsetUtil.US_ASCII.equals(charset) || CharsetUtil.ISO_8859_1.equals(charset)) {
            // ByteBufUtil.getBytes(...) will return a new copy which the AsciiString uses directly
            return new AsciiString(ByteBufUtil.getBytes(this, index, length, true), false);
        }
        return toString(index, length, charset);
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        CharSequence sequence = getCharSequence(readerIndex, length, charset);
        readerIndex += length;
        return sequence;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        checkIndex(index);
        _setByte(index, value);
        return this;
    }

    protected abstract void _setByte(int index, int value);


    // 使用set系列方法必须指定一个索引，也就是说它可以从任意位置设置缓存区中的数据，只要设置的数据不超过缓存区的范围 。
    // 它不会改变读索引readerIndex和写索引writerIndex的值

    @Override
    public ByteBuf setBoolean(int index, boolean value) {   // 在此缓冲区的指定绝对索引处设置指定的布尔值
        setByte(index, value? 1 : 0);
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) { // 在此缓冲区的指定绝对索引处设置指定的16位短整数，忽略指定的16位高阶位 。
        checkIndex(index, 2);
        _setShort(index, value);
        return this;
    }

    protected abstract void _setShort(int index, int value);

    @Override
    public ByteBuf setShortLE(int index, int value) {   // 在此缓冲区中使用小端字节顺序的指定绝对索引处设置指定的16位短整数，忽略指定的16位高阶位
        checkIndex(index, 2);
        _setShortLE(index, value);
        return this;
    }

    protected abstract void _setShortLE(int index, int value);

    @Override
    public ByteBuf setChar(int index, int value) {                  // 在此缓冲区中指定绝对索引处设置指定的32位浮点数
        setShort(index, value);
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {        //在此缓冲区的指定绝对索引处设置指定的24位中等整数，忽略指定的8个高阶位
        checkIndex(index, 3);
        _setMedium(index, value);
        return this;
    }

    protected abstract void _setMedium(int index, int value);

    @Override
    public ByteBuf setMediumLE(int index, int value) {  // 以小端字节顺序在引缓冲区的指定绝对索引处设置指定的24位中位数，忽略指定的值8个高阶位
        checkIndex(index, 3);
        _setMediumLE(index, value);
        return this;
    }

    protected abstract void _setMediumLE(int index, int value);

    @Override
    public ByteBuf setInt(int index, int value) {           // 在此缓冲区的指定绝对索引处设置指定的32位整数
        checkIndex(index, 4);
        _setInt(index, value);
        return this;
    }

    protected abstract void _setInt(int index, int value);

    @Override
    public ByteBuf setIntLE(int index, int value) { // 以小端字节顺序在此缓冲区的指定绝对索引处的设置指定的32位整数
        checkIndex(index, 4);
        _setIntLE(index, value);
        return this;
    }

    protected abstract void _setIntLE(int index, int value);

    @Override
    public ByteBuf setFloat(int index, float value) {
        setInt(index, Float.floatToRawIntBits(value));
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {         // 在此缓冲区的指定绝对索引处设置指定的64位长整数
        checkIndex(index, 8);
        _setLong(index, value);
        return this;
    }

    protected abstract void _setLong(int index, long value);

    @Override
    public ByteBuf setLongLE(int index, long value) {       // 以小端字节顺序在此缓冲区的指定绝对索引处设置指定64位长整数
        checkIndex(index, 8);
        _setLongLE(index, value);
        return this;
    }

    protected abstract void _setLongLE(int index, long value);

    @Override
    public ByteBuf setDouble(int index, double value) {         // 在此缓冲区中指定绝对的索引处设置指定的64位浮点数
        setLong(index, Double.doubleToRawLongBits(value));
        return this;
    }

    // set 系列方法都是向本缓存区设置数据，因此与其他缓存区ByteBuf 交互，就是将其他缓存区的数据写入到本地缓存区
    // 这三个方法都是从指定的索引处开始指定缓冲区src 的数据传输到此缓冲区，我们来看在AbstractByteBuf类中的基本实现如下
    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        setBytes(index, src, 0, src.length);
        return this;
    }

    @Override
    // 从指定的绝对索引处开始将指定的源缓冲区src的数据传输到此缓冲区，直到源缓冲区src的位置达到极限，即源缓冲区src数据读取完
    public ByteBuf setBytes(int index, ByteBuf src) {   // 传递length的大小就是源缓存区src可读区域Readable Bytes 的大小
        setBytes(index, src, src.readableBytes());
        return this;
    }

    @Override
    // 返回从指定的通道读入的实际字节数， 如果是-1 ，则指定的通道被关闭
    public ByteBuf setBytes(int index, ByteBuf src, int length) {   // 传递的srcIndex就是源缓存区src的当前读索引readerIndex的值
        checkIndex(index, length);
        if (src == null) {
            throw new NullPointerException("src");
        }
        if (checkBounds) {
            checkReadableBounds(src, length);
        }
        // 但最后调用它的src.readerIndex()方法，增加源缓存区src的读索引readerIndex的值
        setBytes(index, src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
        return this;
    }



    private static void checkReadableBounds(final ByteBuf src, final int length) {
        if (length > src.readableBytes()) {
            throw new IndexOutOfBoundsException(String.format(
                    "length(%d) exceeds src.readableBytes(%d) where src is: %s", length, src.readableBytes(), src));
        }
    }


    @Override
    // 从指定的绝对索引开始，用NULL(0x00)填充此缓冲区length长度数据
    public ByteBuf setZero(int index, int length) {
        if (length == 0) {
            return this;
        }

        checkIndex(index, length);

        int nLong = length >>> 3;
        int nBytes = length & 7;
        for (int i = nLong; i > 0; i --) {
            _setLong(index, 0);
            index += 8;
        }
        if (nBytes == 4) {
            _setInt(index, 0);
            // Not need to update the index as we not will use it after this.
        } else if (nBytes < 4) {
            for (int i = nBytes; i > 0; i --) {
                _setByte(index, (byte) 0);
                index ++;
            }
        } else {
            _setInt(index, 0);
            index += 4;
            for (int i = nBytes - 4; i > 0; i --) {
                _setByte(index, (byte) 0);
                index ++;
            }
        }
        return this;
    }

    @Override
    // 在给定的索引处设置sequence 对象数据，并返回设置的字节长度
    public int setCharSequence(int index, CharSequence sequence, Charset charset) {
        return setCharSequence0(index, sequence, charset, false);
    }

    private int setCharSequence0(int index, CharSequence sequence, Charset charset, boolean expand) {
        if (charset.equals(CharsetUtil.UTF_8)) {
            int length = ByteBufUtil.utf8MaxBytes(sequence);
            if (expand) {
                ensureWritable0(length);
                checkIndex0(index, length);
            } else {
                checkIndex(index, length);
            }
            return ByteBufUtil.writeUtf8(this, index, sequence, sequence.length());
        }
        if (charset.equals(CharsetUtil.US_ASCII) || charset.equals(CharsetUtil.ISO_8859_1)) {
            int length = sequence.length();
            if (expand) {
                ensureWritable0(length);
                checkIndex0(index, length);
            } else {
                checkIndex(index, length);
            }
            return ByteBufUtil.writeAscii(this, index, sequence, length);
        }
        byte[] bytes = sequence.toString().getBytes(charset);
        if (expand) {
            ensureWritable0(bytes.length);
            // setBytes(...) will take care of checking the indices.
        }
        setBytes(index, bytes);
        return bytes.length;
    }

    @Override
    public byte readByte() {
        checkReadableBytes0(1);
        int i = readerIndex;
        byte b = _getByte(i);
        readerIndex = i + 1;
        return b;
    }

    @Override
    public boolean readBoolean() {
        return readByte() != 0;
    }

    @Override
    public short readUnsignedByte() {
        return (short) (readByte() & 0xFF);
    }

    @Override
    public short readShort() {
        checkReadableBytes0(2);
        short v = _getShort(readerIndex);
        readerIndex += 2;
        return v;
    }

    @Override
    public short readShortLE() {
        checkReadableBytes0(2);
        short v = _getShortLE(readerIndex);
        readerIndex += 2;
        return v;
    }

    @Override
    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    @Override
    public int readUnsignedShortLE() {
        return readShortLE() & 0xFFFF;
    }

    @Override
    public int readMedium() {
        int value = readUnsignedMedium();
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public int readMediumLE() {
        int value = readUnsignedMediumLE();
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public int readUnsignedMedium() {
        checkReadableBytes0(3);
        int v = _getUnsignedMedium(readerIndex);
        readerIndex += 3;
        return v;
    }

    @Override
    public int readUnsignedMediumLE() {
        checkReadableBytes0(3);
        int v = _getUnsignedMediumLE(readerIndex);
        readerIndex += 3;
        return v;
    }

    @Override
    public int readInt() {
        checkReadableBytes0(4);
        int v = _getInt(readerIndex);
        readerIndex += 4;
        return v;
    }

    @Override
    public int readIntLE() {
        checkReadableBytes0(4);
        int v = _getIntLE(readerIndex);
        readerIndex += 4;
        return v;
    }

    @Override
    public long readUnsignedInt() {
        return readInt() & 0xFFFFFFFFL;
    }

    @Override
    public long readUnsignedIntLE() {
        return readIntLE() & 0xFFFFFFFFL;
    }

    @Override
    public long readLong() {
        checkReadableBytes0(8);
        long v = _getLong(readerIndex);
        readerIndex += 8;
        return v;
    }

    @Override
    public long readLongLE() {
        checkReadableBytes0(8);
        long v = _getLongLE(readerIndex);
        readerIndex += 8;
        return v;
    }

    @Override
    public char readChar() {
        return (char) readShort();
    }

    @Override
    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public ByteBuf readBytes(int length) {
        checkReadableBytes(length);
        if (length == 0) {
            return Unpooled.EMPTY_BUFFER;
        }

        ByteBuf buf = alloc().buffer(length, maxCapacity);
        buf.writeBytes(this, readerIndex, length);
        readerIndex += length;
        return buf;
    }

    @Override
    public ByteBuf readSlice(int length) {
        checkReadableBytes(length);
        ByteBuf slice = slice(readerIndex, length);
        readerIndex += length;
        return slice;
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        checkReadableBytes(length);
        ByteBuf slice = retainedSlice(readerIndex, length);
        readerIndex += length;
        return slice;
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        readBytes(dst, 0, dst.length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        readBytes(dst, dst.writableBytes());
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        if (checkBounds) {
            if (length > dst.writableBytes()) {
                throw new IndexOutOfBoundsException(String.format(
                        "length(%d) exceeds dst.writableBytes(%d) where dst is: %s", length, dst.writableBytes(), dst));
            }
        }
        readBytes(dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        // 检测ByteBuf是否可读
        // 检测其可读长度是否小于length
        checkReadableBytes(length);
        // 数据的具体读取由子类实现 ， readBytes()方法调用getBytes()方法从当前的读索引开始，将 length个字节复制到目标byte数组中。
        // 由于不同的子类对应不同的复 制操作，所以AbstractByteBuf类中的getBytes()方法是一个抽象方 法，留给子类来实现。
        getBytes(readerIndex, dst, dstIndex, length);
        // 修改读索引
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        int length = dst.remaining();
        checkReadableBytes(length);
        getBytes(readerIndex, dst);
        readerIndex += length;
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length)
            throws IOException {
        checkReadableBytes(length);
        int readBytes = getBytes(readerIndex, out, length);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public int readBytes(FileChannel out, long position, int length)
            throws IOException {
        checkReadableBytes(length);
        int readBytes = getBytes(readerIndex, out, position, length);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        checkReadableBytes(length);
        getBytes(readerIndex, out, length);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf skipBytes(int length) {          // 方法，将该缓冲区的当前readerIndex增加指定的长度
        checkReadableBytes(length);
        readerIndex += length;
        return this;
    }
    // 注意read系列和skipBytes方法能改变读索引readerIndex的值，除了它们，只剩下readerIndex(int readerIndex),setIndex(int readerIndex,int writerIndex)
    //

    @Override
    public ByteBuf writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
        return this;
    }

    @Override
    public ByteBuf writeByte(int value) {
        ensureWritable0(1);
        _setByte(writerIndex++, value);
        return this;
    }

    @Override
    public ByteBuf writeShort(int value) {
        ensureWritable0(2);
        _setShort(writerIndex, value);
        writerIndex += 2;
        return this;
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        ensureWritable0(2);
        _setShortLE(writerIndex, value);
        writerIndex += 2;
        return this;
    }

    @Override
    public ByteBuf writeMedium(int value) {
        ensureWritable0(3);
        _setMedium(writerIndex, value);
        writerIndex += 3;
        return this;
    }

    @Override
    public ByteBuf writeMediumLE(int value) {
        ensureWritable0(3);
        _setMediumLE(writerIndex, value);
        writerIndex += 3;
        return this;
    }

    @Override
    public ByteBuf writeInt(int value) {
        ensureWritable0(4);
        _setInt(writerIndex, value);
        writerIndex += 4;
        return this;
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        ensureWritable0(4);
        _setIntLE(writerIndex, value);
        writerIndex += 4;
        return this;
    }

    @Override
    public ByteBuf writeLong(long value) {
        ensureWritable0(8);
        _setLong(writerIndex, value);
        writerIndex += 8;
        return this;
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        ensureWritable0(8);
        _setLongLE(writerIndex, value);
        writerIndex += 8;
        return this;
    }

    @Override
    public ByteBuf writeChar(int value) {
        writeShort(value);
        return this;
    }

    @Override
    public ByteBuf writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
        return this;
    }

    @Override
    public ByteBuf writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        ensureWritable(length);
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        writeBytes(src, 0, src.length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        writeBytes(src, src.readableBytes());
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        if (checkBounds) {
            checkReadableBounds(src, length);
        }
        writeBytes(src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
        return this;
    }

    @Override
    // AbstractByteBuf的写操作writeBytes()方法涉及扩容，在扩容时 除了合法校验，还需要计算新的容量值，若内存大小为2的整数次幂，
    // 则AbstractByteBuf的子类比较好分配内存，因此扩容后的大小必须是 2的整数次幂，计算逻辑复杂
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        // 确保可写，当容量不足时自动扩容
        ensureWritable(length);
        // 缓冲区真正的写操作由子类实现
        setBytes(writerIndex, src, srcIndex, length);
        // 调整写索引
        writerIndex += length;
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        int length = src.remaining();
        ensureWritable0(length);
        setBytes(writerIndex, src);
        writerIndex += length;
        return this;
    }

    @Override
    public int writeBytes(InputStream in, int length)
            throws IOException {
        ensureWritable(length);
        int writtenBytes = setBytes(writerIndex, in, length);
        if (writtenBytes > 0) {
            writerIndex += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        ensureWritable(length);
        int writtenBytes = setBytes(writerIndex, in, length);
        if (writtenBytes > 0) {
            writerIndex += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        ensureWritable(length);
        int writtenBytes = setBytes(writerIndex, in, position, length);
        if (writtenBytes > 0) {
            writerIndex += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public ByteBuf writeZero(int length) {
        if (length == 0) {
            return this;
        }

        ensureWritable(length);
        int wIndex = writerIndex;
        checkIndex0(wIndex, length);

        int nLong = length >>> 3;
        int nBytes = length & 7;
        for (int i = nLong; i > 0; i --) {
            _setLong(wIndex, 0);
            wIndex += 8;
        }
        if (nBytes == 4) {
            _setInt(wIndex, 0);
            wIndex += 4;
        } else if (nBytes < 4) {
            for (int i = nBytes; i > 0; i --) {
                _setByte(wIndex, (byte) 0);
                wIndex++;
            }
        } else {
            _setInt(wIndex, 0);
            wIndex += 4;
            for (int i = nBytes - 4; i > 0; i --) {
                _setByte(wIndex, (byte) 0);
                wIndex++;
            }
        }
        writerIndex = wIndex;
        return this;
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        int written = setCharSequence0(writerIndex, sequence, charset, true);
        writerIndex += written;
        return written;
    }

    @Override
    public ByteBuf copy() {
        return copy(readerIndex, readableBytes());
    }

    @Override
    public ByteBuf duplicate() {
        ensureAccessible();
        return new UnpooledDuplicatedByteBuf(this);
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return duplicate().retain();
    }

    @Override
    public ByteBuf slice() {    // 返回该缓冲区可读字节的一切切片
        return slice(readerIndex, readableBytes());
    }

    @Override
    public ByteBuf retainedSlice() {    // 返回该缓冲区可读写的字节保留片
        return slice().retain();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        ensureAccessible();
        return new UnpooledSlicedByteBuf(this, index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return slice(index, length).retain();
    }

    @Override
    public ByteBuffer nioBuffer() {
        return nioBuffer(readerIndex, readableBytes());
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return nioBuffers(readerIndex, readableBytes());
    }

    @Override
    public String toString(Charset charset) {
        return toString(readerIndex, readableBytes(), charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return ByteBufUtil.decodeString(this, index, length, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {      // 定位指定的字节值value在此缓冲区中第一次出现的位置 ， 如果找不到就返回-1
        return ByteBufUtil.indexOf(this, fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return bytesBefore(readerIndex(), readableBytes(), value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        checkReadableBytes(length);
        return bytesBefore(readerIndex(), length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        int endIndex = indexOf(index, index + length, value);
        if (endIndex < 0) {
            return -1;
        }
        return endIndex - index;
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        ensureAccessible();
        try {
            return forEachByteAsc0(readerIndex, writerIndex, processor);
        } catch (Exception e) {
            PlatformDependent.throwException(e);
            return -1;
        }
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        checkIndex(index, length);
        try {
            return forEachByteAsc0(index, index + length, processor);
        } catch (Exception e) {
            PlatformDependent.throwException(e);
            return -1;
        }
    }

    int forEachByteAsc0(int start, int end, ByteProcessor processor) throws Exception {
        for (; start < end; ++start) {
            if (!processor.process(_getByte(start))) {
                return start;
            }
        }

        return -1;
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        ensureAccessible();
        try {
            return forEachByteDesc0(writerIndex - 1, readerIndex, processor);
        } catch (Exception e) {
            PlatformDependent.throwException(e);
            return -1;
        }
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        checkIndex(index, length);
        try {
            return forEachByteDesc0(index + length - 1, index, processor);
        } catch (Exception e) {
            PlatformDependent.throwException(e);
            return -1;
        }
    }

    int forEachByteDesc0(int rStart, final int rEnd, ByteProcessor processor) throws Exception {
        for (; rStart >= rEnd; --rStart) {
            if (!processor.process(_getByte(rStart))) {
                return rStart;
            }
        }
        return -1;
    }

    @Override
    public int hashCode() {
        return ByteBufUtil.hashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ByteBuf && ByteBufUtil.equals(this, (ByteBuf) o));
    }

    @Override
    public int compareTo(ByteBuf that) {
        return ByteBufUtil.compare(this, that);
    }

    @Override
    public String toString() {
        if (refCnt() == 0) {
            return StringUtil.simpleClassName(this) + "(freed)";
        }

        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append("(ridx: ").append(readerIndex)
            .append(", widx: ").append(writerIndex)
            .append(", cap: ").append(capacity());
        if (maxCapacity != Integer.MAX_VALUE) {
            buf.append('/').append(maxCapacity);
        }

        ByteBuf unwrapped = unwrap();
        if (unwrapped != null) {
            buf.append(", unwrapped: ").append(unwrapped);
        }
        buf.append(')');
        return buf.toString();
    }

    protected final void checkIndex(int index) {
        checkIndex(index, 1);
    }

    protected final void checkIndex(int index, int fieldLength) {
        ensureAccessible();
        checkIndex0(index, fieldLength);
    }

    private static void checkRangeBounds(final String indexName, final int index,
            final int fieldLength, final int capacity) {
        if (isOutOfBounds(index, fieldLength, capacity)) {
            throw new IndexOutOfBoundsException(String.format(
                    "%s: %d, length: %d (expected: range(0, %d))", indexName, index, fieldLength, capacity));
        }
    }

    final void checkIndex0(int index, int fieldLength) {
        if (checkBounds) {
            checkRangeBounds("index", index, fieldLength, capacity());
        }
    }

    protected final void checkSrcIndex(int index, int length, int srcIndex, int srcCapacity) {
        checkIndex(index, length);
        if (checkBounds) {
            checkRangeBounds("srcIndex", srcIndex, length, srcCapacity);
        }
    }

    protected final void checkDstIndex(int index, int length, int dstIndex, int dstCapacity) {
        checkIndex(index, length);
        if (checkBounds) {
            checkRangeBounds("dstIndex", dstIndex, length, dstCapacity);
        }
    }

    protected final void checkDstIndex(int length, int dstIndex, int dstCapacity) {
        checkReadableBytes(length);
        if (checkBounds) {
            checkRangeBounds("dstIndex", dstIndex, length, dstCapacity);
        }
    }

    /**
     * Throws an {@link IndexOutOfBoundsException} if the current
     * {@linkplain #readableBytes() readable bytes} of this buffer is less
     * than the specified value.
     */
    protected final void checkReadableBytes(int minimumReadableBytes) {
        checkPositiveOrZero(minimumReadableBytes, "minimumReadableBytes");
        checkReadableBytes0(minimumReadableBytes);
    }

    protected final void checkNewCapacity(int newCapacity) {
        ensureAccessible();
        if (checkBounds) {
            if (newCapacity < 0 || newCapacity > maxCapacity()) {
                throw new IllegalArgumentException("newCapacity: " + newCapacity +
                        " (expected: 0-" + maxCapacity() + ')');
            }
        }
    }

    private void checkReadableBytes0(int minimumReadableBytes) {
        ensureAccessible();
        if (checkBounds) {
            if (readerIndex > writerIndex - minimumReadableBytes) {
                throw new IndexOutOfBoundsException(String.format(
                        "readerIndex(%d) + length(%d) exceeds writerIndex(%d): %s",
                        readerIndex, minimumReadableBytes, writerIndex, this));
            }
        }
    }

    /**
     * Should be called by every method that tries to access the buffers content to check
     * if the buffer was released before.
     */
    protected final void ensureAccessible() {
        if (checkAccessible && !isAccessible()) {
            throw new IllegalReferenceCountException(0);
        }
    }

    final void setIndex0(int readerIndex, int writerIndex) {
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
    }

    final void discardMarks() {
        markedReaderIndex = markedWriterIndex = 0;
    }
}
