package io.netty.actual.combat.e1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.io.BufferedReader;

public class ByteBufReferenceTest {




    // Netty的ByteBuf的内存回收工作是通过引用计数的方式管理，JVM 中使用计数器（一种GC算法）来标记对象是否不可达进而收回，注 GC是Garbage
    // Collection的缩写，即Java 中的垃圾回收机制，Netty 也使用了这种手段来对ByteBuf引用进行计数，Netty 采用计数器来追踪ByteBuf的生命周期
    // 是对PooledByteBuf的支持，二是能忙的发现那些可回收的ByteBuf（非Pooled）以便提升ByteBuf 的分配和销毁的效率 。
    // 插入话题，什么是Pooled（池化）的ByteBuf缓冲区呢？ 在通信程序的执行过程上，ByteBuf 缓冲区实例会被频繁的创建，使用，释放，大家都知道。频繁的创建对象，内存的
    // 分配，释放 ，系统的开销大，性能低，如何提升性能，提高ByteBuf 实例的使用率呢？ 从Netty4版本开始，新增加了对象池化的机制 。
    // 即创建一个Buffer对象池，将没有被引用的ByteBuf 对象，放入到对象缓存池中，当需要时，则重新从对象缓存池中取出，而不需要重新创建
    // 回到正题，引用计数的大致规则如下 ， 在默认情况下，当创建完一个ByteBuf 时，它的引用为1，每次调用retain()方法时，它的引用就加1
    // 每次调用release()方法时，就将引用计减1 ， 如果引用为0 ， 再次访问这个ByteBuf 对象，将会抛出异常，如果引用为0 ， 表示这个ByteBuf
    // 没有哪个进程引用它， 它占用的内存需要回收，在下面的例子中，多闪用到了retain() 和release()方法 ， 运行后可以看到效果

    public static void main(String[] args) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();;
        System.out.println("after create : " + byteBuf.refCnt());
        byteBuf.retain();
        System.out.println("after retain: " + byteBuf.refCnt());
        byteBuf.release();
        byteBuf.release();
        // Exception in thread "main" io.netty.util.IllegalReferenceCountException: refCnt: 0, increment: 1
        //	at io.netty.util.internal.ReferenceCountUpdater.retain0(ReferenceCountUpdater.java:168)
        //	at io.netty.util.internal.ReferenceCountUpdater.retain(ReferenceCountUpdater.java:151)
        // 原因是： 在此之前，缓冲区的buffer 的引用计数已经为0，不能再retain了，也就是说，在Netty中，引用计数为0 的缓冲区不能再继续使用
        // 为了确保引用计数不会混乱， 在Netty 的业务处理开发过程上，应该坚持一个原因，retain和release方法应该结对使用，简单的来说，在一个方法中，
        // 调用了retain，就应该调用一次release .
        //  public void handlerMethodA(ByteBuf buf){
        //        buf.retain();
        //        try {
        //            //handlemethodB();
        //        }catch (Exception e ){
        //
        //        }finally {
        //            buf.release();
        //        }
        //    }
        // 如果retain和release()这两个方法，一次都不调用呢 ? 则在缓冲区使用完成后，调用一次relese(）就是释放一次，例如在Netty流水线上，中间所有的Handler
        // 业务处理器处理完ByteBuf之后，直接传递给下一个，由最后一个Handler 负责调用release()来翻译缓冲区的内部空间
        // 当引用计数器已经为0 ，Netty 会进行ByteBuf 的回收，等待下一次分配分配 ，
        // 1. Pooled 池化的ByteBuf内存，回收方法是，放入可以重新分配的ByteBuf 池子，等待下一次分配 ， UnPooled未池化的ByteBuf缓冲区
        // 回收分为两种情况 ， 如果是堆（Heap)结构缓冲，会被JVM的垃圾回收机制回收，如果是Direct类型，调用本地方法释放外部内存（unsafe.freeMemory)
        System.out.println(" after release: " + byteBuf.refCnt());
        byteBuf.retain();
        System.out.println("after retain:" + byteBuf.refCnt());
        //

    }



}
