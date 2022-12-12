package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class SliceTest {
    public static void main(String[] args) {
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(9,100);
        PrintAttribute.print("动作：分配ByteBuf(9,100):" ,buffer);
        buffer.writeBytes(new byte[]{1,2,3,4});
        PrintAttribute.print("动作：写入4个字节（1,2,3,4）"  ,buffer);
        ByteBuf slice = buffer.slice();
        PrintAttribute.print("动作：切片：" , slice);
        // 调用slice()方法后，返回的切片是一个新的ByteBuf对象，该对象有几个重要的属性
        // 1. readerIndex(读指针)的值为0
        // 2. writerIndex(写指针)的值为源ByteBuf的readableBytes()可读字节数
        // 3. maxCapacity(最大容量)的值为源ByteBuf的readableBytes() 可读字节数

        // 切片后新的ByteBuf 有两个特点 ：
        // 切片不可写入，原因是，maxCapacity与writerIndex值相同
        // 切片和源ByteBuf的可读字节数相同，原因是： 切片后的可读字节数为自己的属性writerIndex-readerIndex， 也就是源ByteBuf 的readableBytes() - 0

        // 切片后新的ByteBuf和源ByteBuf的关联性
        // 切片不会复制源ByteBuf 的底层数据，底层数组和源ByteBuf的底层数组是同一个
        // 切片不会改变源ByteBuf 的引用计数器
        // 从根本上来说，slice()无参数的方法所生成的切片就是源ByteBuf 可读部分的浅层复制 。

    }
}
