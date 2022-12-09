
package io.netty.actual.combat.e1;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

public class ChannelFutureTest {


    public static void main(String[] args) {
        // connect是异步的，仅提交异步任务
        ChannelFuture future = null;//bootstrap.connect(new InetSocketAddress("www.manning.com",80));
        // connect 是异步任务真正执行完成，future回调监听器才会执行
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    System.out.println("Connection established");
                } else {
                    System.out.println("Connection attempt failed");
                    future.cause().printStackTrace();
                }
            }
        });
        // GenericFutureListener 接口在Netty中是一个基础类型的接口， 在网络编程中的异步回调中，一般使用Netty中提供的某个子接口。
        // 如ChannelFutureListener接口， 在上面代码中使用了这个子接口。
        // Netty 的出站与入站异步回调。
        // 以最经典的NIO出站操作-write为例，说明一下ChannelFuture的使用。
        // 在调用write操作后，Netty 并没有完成对Java   NIO 底层连接的写入操作，因为是异步执行的，代码如下
        // write 输出方法，返回的是一个异步任务
        // ChannelFutrue future = ctx.channel().write(msg);
        // 为异步任务，加上监听器
        // future.addListenr(new ChannelFutrueListener()){
        //              new ChannelFutureListener(){
        //                  @Override
        //                          public void operationCompete(ChannelFuture future){
        //                              // write 操作完成后回调代码
        //                         }
        //                }
        // }
        // 在调用write 操作后，立即返回，返回的是一个ChannelFuture接口的实现，通过这个实例，可以绑定异步回调监听器， 这里的异步回调逻辑需要我们编写 。
        // 如果大家运行以止EchoServer的实践案例，就会发现一个很大的问题， 客户端接收到回显信息和发送到服务器的信息， 不是一对一的输出的，看到的比较多的情况是 ，
        // 客户端发出很多的信息后，客户端才收到一次服务器的回调。
        // 这是什么原因呢？这就是网络通信中的粘包/和半包问题， 对于这个问题的解决这群，在后面会非常详细的解答，这里暂时搁置， 粘包/半包的问题的出现 。
        // 说明了一个问题， 仅权基于Java Nio 开发一套高性能的，没有Bug的通信服务程序，远远没有大家想象的简单，有一个系列的坑， 一大堆的基础问题等着
        // 大家去解决 。
        // 在进行大型的Java通信程序的开发时，尽量基于一些实现子成熟，稳定的基础通道的Java源中间件，如Netty，这些中间伯已经帮助大家解决子很多的
        // 基础问题，如前面出现的粘包， 半包问题。
        // 至此，大家已经学习了Java NIO Reactor反应器模式，Future模式，这些都子时学习Netty 应用开发的基础，基础已经差不多了，但是
        // 首先引用Netty 官网的内容对Netty进行一个正式的介绍
        // Netty 是为了快速开发可维护性高性能的，高可扩展，网络服务器和客户端程序而提供的异步事件鸡翅基础框架和工具，换句说,Netty 是一个
        // Java NIO 客户端/服务器框架，基于Netty ，可以快速轻松的开发网络服务器和客户端的应用程序，与直接使用JavaNIO 相比，Netty
        // 给大家选出了非常优美的轮子， 它可以大大的简化网络编程的流程， 例如，Netty 极大的简化了TCP,UDP dupbb，Http WEb 服务程序的开发 .
        // Netty 的目标之二， 是要使开发可以做到快速，轻松， 除了做快速和轻松的开发，TCP/UDP等自定义协议的通信程序之外 ， Netty 经过精心
        // 设计，还可以做到快速和轻松的开发应用层协议的程序，如FTP，SMTP ， HTTP 以及其他的传统应用层协议 。
        // Netty 的目标之二，是要做到高性能，高可扩展性， 基于Java的NIO ，Netty 设计了一套优秀的Reactor反应器模式，后面会详细的介绍
        // Netty 中反应器模式的实现， 基于Netty反应器模式实现的Channel 通道，Handler 处理器等类，能快速的扩展以覆盖不同的协议，完成不同的业务处理
        // 大量的应用类。
        //

    }
}