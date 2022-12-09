package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import sun.misc.IOUtils;
import sun.nio.ch.IOUtil;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;

public class UserBuffer {

    public static void main(String[] args) throws Exception {
        // 调用allocate方法，而不是使用new
        IntBuffer intBuffer = IntBuffer.allocate(20);
        // 输出buffer的主要属性值
        System.out.println("-------------before allocate ----------------");
        System.out.println("position=" + intBuffer.position());
        System.out.println("limit=" + intBuffer.limit());
        System.out.println("capacity=" + intBuffer.capacity());


        for (int i = 0; i < 5; i++) {
            // 写入一个整数到缓冲区
            intBuffer.put(i);
        }
        System.out.println("-------------after allocate ----------------");
        System.out.println("position=" + intBuffer.position());
        System.out.println("limit=" + intBuffer.limit());
        System.out.println("capacity=" + intBuffer.capacity());

        // 翻转缓冲区,从写模式翻转成读模式
        intBuffer.flip();
        //  输出缓冲区的主要属性值
        System.out.println("-------------after flip ----------------");
        System.out.println("position=" + intBuffer.position());
        System.out.println("limit=" + intBuffer.limit());
        System.out.println("capacity=" + intBuffer.capacity());
        // 在调用flip方法后，之前写入的模式下的position的值5，变成了可读上限limit值5，而新的读写模式下的position的值，简单粗暴的变成了0，表示从开始读取 。
        // 对flip()方法的写入到读取转换的规则，详细的介绍如下 ：
        // 首先，设置可读的长度上限limit,将写模式下的缓冲区中的内容的最后写入位置position值作为读模式下的limit的上限值 。
        // 其中把读的起始位置position的值设置为0，表示从头开始读。
        // 先读取两个
        for (int i = 0; i < 2; i++) {
            int j = intBuffer.get();
            System.out.println("j = " + j);
        }
        System.out.println("-------------after get 2 int  ----------------");
        System.out.println("position=" + intBuffer.position());
        System.out.println("limit=" + intBuffer.limit());
        System.out.println("capacity=" + intBuffer.capacity());

        // 再读取3个
        for (int i = 0; i < 3; i++) {
            int j = intBuffer.get();
            System.out.println("j = " + j);
        }
        // 输出缓冲区的主要属性值
        System.out.println("-------------after get 3 int  ----------------");
        System.out.println("position=" + intBuffer.position());
        System.out.println("limit=" + intBuffer.limit());
        System.out.println("capacity=" + intBuffer.capacity());

        // 从程序的输出结果，我们可以看到，读取操作会改变可读位置的position的值，而limit值不会改变，如果position的值和limit的值相等
        // 表示所有的数据读取完成，position指向了一个没有数据的元素位置，已经不能再读了，此时再读，会抛出Exception in thread "main" java.nio.BufferUnderflowException异常
        // 这里强调一下，在读完之后，是否可以立即进行写入模式呢？ 不能，现在还处于读取模式，我们必须调用Buffer.clear()或Buffer.compact()
        // 即清空或者压缩缓冲区，才能变成写入模式，让其重新可写。 另外还有一个问题，缓冲区不是可以重复读呢？答案是可以的。
        // int j =     intBuffer.get();
        // System.out.println("j = " + j);
        // 倒带
        intBuffer.rewind();
        System.out.println("-------------after rewind  ----------------");
        System.out.println("position=" + intBuffer.position());
        System.out.println("limit=" + intBuffer.limit());
        System.out.println("capacity=" + intBuffer.capacity());

        // rewind()方法，主要是调整缓冲区的position属性，具体的调整规则如下
        // position重置为0，所以可以重新读取缓冲区中所有的数据 。
        // limit 保持不变，数据量还是和之前的一样，仍然表示能从缓冲区中读取多少个元素的数据
        // mark 标记被清理，表示之前的临时位置不能再用了。


        // mark()和reset()
        // Buffer.mark()方法的作用是将当前position的值保存起来，放到mark属性中， 让mark属性记录这个临时位置，之后，可以调用Buffer.reset()方法
        // 将mark的值恢复到position中
        // 也就是说，Buffer.mark()和Buffer.reset()方法是配套使用的，两个方法都需要内部mark()属性支持。
        //


        // 省略了缓冲区的读取，倒带的代码，具体看源码工程
        for (int i = 0; i < 5; i++) {
            if (i == 2) {
                // 临时保存，标记一下第3个位置
                intBuffer.mark();
            }
            // 读取元素
            int j = intBuffer.get();
            System.out.println("j=====" + j);
        }
        System.out.println("-------------after reRead  ----------------");
        System.out.println("position=" + intBuffer.position());
        System.out.println("limit=" + intBuffer.limit());
        System.out.println("capacity=" + intBuffer.capacity());

        System.out.println("-------------after reset  ----------------");
        // 打前面保存在mark中的值恢复到position
        intBuffer.reset();
        // 输出缓冲区的属性值
        for (int i = 2; i < 5; i++) {
            int j = intBuffer.get();
            System.out.println("j====" + j);
        }

        // 调用reset方法之后，position的值为2 ， 此时去读取缓冲区，输出后面的三个元素为2，3，4，
        // clear()清空缓冲区
        // 在读取模式下，调用clear()方法将缓冲区切换为写入模式，此方法会将position清零，limit设置为capacity最大容量值，可以一直写入，直到缓冲区写满

        System.out.println("-------------after clear  ----------------");
        intBuffer.clear();
        System.out.println("position=" + intBuffer.position());
        System.out.println("limit=" + intBuffer.limit());
        System.out.println("capacity=" + intBuffer.capacity());
        // 在缓冲区处于读取模式时，调用clear()缓冲区会被切换成写入模式，调用clear()方法之后，我们可以看到清空了position的值
        // 即设置写入的起始位置为0，并且写入的上限的最大容量 。
        // 3.3.8 使用Buffer类的基本步骤
        // 总体来说，使用Java Buffer 类的基本步骤如下
        // 1. 使用创建子类实例对象的allocate()方法，创建一个Buffer 类的实例对象。
        // 2. 调用put方法，将数据写入到缓冲区中
        // 3. 写入完成后，在开始读取数据前，调用Buffer.flip()方法，将缓冲区的转换成读模式 。
        // 4. 调用get方法，从缓冲区中读取数据
        // 5. 读取完成后，调用Buffer.clear()或Buffer.compact()方法，将缓冲区转换为写入模式
        // 3.4 详解Nio  Channel 通道类
        // 前面讲到，NIO中一个连接就是用一个Channel(通道)来表示，大家知道，从更广泛的层面来说，一个通道可以表示一个底层的文件描述符，例如硬件设备
        // 文件，网络连接等，然而远远不止如此，除了可以对就的底层文件描述符， Java NIO 的通道还可更加细化，例如 ，对应的不同网络传输协议类型
        // 在Java 中都有不同的NIO Channel(通道)实现。
        // 3.4.1 通道，的主要类型
        // 这里不对纷繁复杂的JavaNIO 通道类型进行过多的描述，仅仅聚焦于介绍其中最为重要的4种Channel(通道的)实现， FileChannel ， SocketChannel
        // ServerSocketChannel , DatagramChannel
        // 对于以上四种通道，说明如下 ：
        // 1. FileChannel文件通道，用于文件的数据读写
        // 2. SocketChannel 套接字通道，用于Socket套接字TCP连接的数据读写
        // 3. ServerSocketChannel服务器嵌套通道（或服务器监听通道），允许我们监听TCP连接请求，为每个监听的请求，创建一个SocketChannel套接字通道 。
        // 4. Datagrame 的Channel 数据报通道，用于UDP协议数据的读写。
        // 这个四种通道，涵盖了文件IO,TCP 网络，UDP IO基础IO,下面从Channel(通道)的获取，读取，写入，关闭四个重要的操作，来对四种通道进行简单的介绍 。
        // 3.4.2 FileChannel 文件通道
        // FileChannel 是专门操作文件的通道，通过FileChannel，即可以从一个文件中读取数据，也可以将数据写入到文件中，特别申明一下，FileChannel
        // 为阻塞模式，不能设置为非阻塞模式
        // 下面分别为：FileChannel获取，读取，写入， 关闭四个操作
        // 1. 获取FileChannel通道
        // 可以通过文件的输入流，输出流获取FileChannel文件通道，示例如下
        // 创建一条文件输入流
        FileInputStream fis = new FileInputStream("");
        // 获取文件流通道
        FileChannel channel = fis.getChannel();

        // 创建一条文件输出流
        FileOutputStream fos = new FileOutputStream("");
        // 获取文件流的通道
        FileChannel channel1 = fos.getChannel();
        // 也可以通过RandomAccessFile文件随机访问类， 获取FileChannel文件通道
        // 创建RandomAccessFile随机访问对象
        RandomAccessFile accessFile = new RandomAccessFile("filename.txt","rx");
        // 获取文件流的通道
        FileChannel channel2 = accessFile.getChannel();

        // 2 读取FileChannel通道
        // 在大部分应用场景，从通道读取数据都会调用通道的int read(ByteBuffer buf )方法，它从通道读取到数据写入到ByteBuffer缓冲区，并且
        // 返回读取到的数据量
        // 获取一个字节缓冲区
        ByteBuffer buf = ByteBuffer.allocate(1024);
        int lenth = -1;
        // 调用通道的read方法，读取数据并买入字节类型的缓冲区
        while((lenth = channel2.read(buf)) !=-1 ){
                //
        }
        // 4. 关闭通道
        // 5. 强制刷新到磁盘
        // 在将缓冲区写入通道时，出于性能原因，操作系统不可能每次都实时的将数据写入到磁盘，如果需要保证写入通道的缓冲数据，最终，都真正的
        // 写入磁盘，可以调用FileChannel.force()方法
        // 强制刷新到磁盘
        //channel.force();







    }
}
