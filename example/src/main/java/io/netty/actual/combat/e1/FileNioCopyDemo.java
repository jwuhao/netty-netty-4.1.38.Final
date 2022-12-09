package io.netty.actual.combat.e1;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class FileNioCopyDemo {

    public static void main(String[] args)  throws Exception{


        String sourcePath = "";

        // 特别强调一下，除了FileChannel的通道操作外，还需要注意ByteBuffer的模式切换，新建的ByteBuffer，默认是写入模式，可以作为
        // inChannel.read(ByteBuffer)的参数，inChannel.read方法从通道inChannel读到的数据写入到ByteBuffer
        // 特别强调一下，除了FileChannel的通道操作之外，还需要注意ByteBuffer的模式切换，
        //
        // SocketChannel 套接字通道
        // 在NIO 中，涉及网络连接的通道有两个，一个是SocketChannel负责连接传输，另外一个是ServerSocketChannel负责连接监听 。
        // NIO 中的SocketChannel传输通道，与OIO中的Socket类对应
        // NIO中的ServerSocketChannel监听通道，对应OIO 中的ServerSocket
        // ServerSocketChannel应用于服务器，而SocketChannel同时处于服务器和客户端，换句话说，对应于一个连接，两端都有一个负责传输的
        // SocketChannel传输通道 。
        // ServerSocketChannel应用于服务端，而SocketChannel 同时处于服务器端和客户端，换句话说，对应于一个连接，两端都有一个负责
        // 传输的SocketChannel传输通道 。
        // 无论是ServerSocketChannel，还是SocketChannel，都支持阻塞和非阻塞两种模式，何进行模式的设置呢？调用configureBlocking方法，具体如下
        // 1. socketChannel.configureBlocking(false) 设置为非阻塞模式
        // 2. socketChannel.configureBlocking(true) 设置为阻塞模式
        // 在阻塞模式下， SocketChannel通道的connect连接，read读，write写操作，都是同步的和阻塞式的，在效率上与Java旧的OIO 的面向流的阻塞
        // 式读写操作相同，因此，在这里不介绍阻塞模式下的通道具体操作，在非阻塞模式下，通道的操作是异步的，高效率的，这也是相对于传统的
        // OIO的优势所在，下面详细介绍在非阻塞模式下通过通道的打开，读写和关闭操作等操作。
        // 1. 获取SocketChannel 传输通道
        // 在客户端先通过SocketChannel静态方法open()获得一个套接字传输通道，然后，将socket套接字设置为非阻塞模式，最后，通过connect()
        //  的实例方法，对服务器的IP和端口发起连接 。
        // 获取一个套接字传输通道
        SocketChannel socketChannel = SocketChannel.open();
        // 设置为非阻塞模式
        socketChannel.configureBlocking(false);
        // 对服务器的IP和商品发起连接
        socketChannel.connect(new InetSocketAddress("127.0.0.1",80));
        // 非阻塞情况下， 与服务器的连接可能还没有真正的建立，socketChannel.connect方法就返回了。
        // 因此需要不断的自旋，检测当前是否连接到主机 。
        while(! socketChannel.finishConnect()){
            // 不断的自旋，等待，或者做一些其他的事情
        }
        // 在服务端，如何获取传输套接字呢？
        // 洋奴新连接事件到来时， 在服务端的ServerSocketChannel能成功地查询出一个新连接事件，并且通过调用服务端ServerSocketChannel
        // 监听套接字accept()方法，来获取新连接的套接字通道
        // 在读取数据时，因为是异步，因此我们必须检测read的返回值，以便判断当前是否读取到了数据，read()方法的返回值，是读取的字节数
        // 如果返回值是-1，那么表示读取到对方输出结束标志，对方已经输出结束，准备关闭连接，通过read()方法读数据，本身就是很简单的，比较困难
        // 的是在非阻塞模式下，如何知道何时是可读的呢？这就需要用NIO 的新组件Selector通道选择器，稍后介绍 。

        // 3 写入到SocketChannel传输通道
        // 和前面的把数据写入到FileChannel文件通道一样，大部分应用场景都会调用通道int write(ByteBuffer buf)方法
        // 写入前需要读取缓冲区，要求ByteBuffer是读取模式
        // buffer.flip();
        // socketChannel.write(buffer);
        //

    }


}
