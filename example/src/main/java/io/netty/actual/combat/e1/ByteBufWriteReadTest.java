package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class ByteBufWriteReadTest {


    public static void main(String[] args) {
        // ByteBuf的基本使用分为三部分
        // 1.分配一个ByteBuf实例
        // 2. 向ByteBuf 写数据
        // 3. 从ByteBuf读数据
        // 这里用默认的分配器，分配了一个初始化容量为9，最大限制为100的字节的缓冲区， 关于ByteBuf的实例的分配，稍后具体详细介绍
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(9,100);
        System.out.println("动作，分配了ByteBuf (9,100): " + buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        System.out.println("动作：写入4个字节（1，2，3，4）:"+buf);
        System.out.println("start =============get==============");
        getByteBuf(buf);
        System.out.println("动作，取数据ByteBuf " + buf);
        readByteBuf(buf);
        System.out.println("动作读取完 " + buf);


    }


    public  static void  readByteBuf(ByteBuf buf){
        while (buf.isReadable()){
            System.out.println("取一个字节 ：" + buf.readByte());
        }
    }
    public static void getByteBuf(ByteBuf buf){
        for(int i = 0 ;i < buf.readableBytes(); i ++){
            System.out.println("读取一个字节：" + buf.getByte(i));
        }
    }
}
