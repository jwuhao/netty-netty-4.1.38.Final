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
package io.netty.util;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.util.internal.StringUtil.simpleClassName;

/**
 * A {@link Timer} optimized for approximated I/O timeout scheduling.
 *
 * <h3>Tick Duration</h3>
 *
 * As described with 'approximated', this timer does not execute the scheduled
 * {@link TimerTask} on time.  {@link HashedWheelTimer}, on every tick, will
 * check if there are any {@link TimerTask}s behind the schedule and execute
 * them.
 * <p>
 * You can increase or decrease the accuracy of the execution timing by
 * specifying smaller or larger tick duration in the constructor.  In most
 * network applications, I/O timeout does not need to be accurate.  Therefore,
 * the default tick duration is 100 milliseconds and you will not need to try
 * different configurations in most cases.
 *
 * <h3>Ticks per Wheel (Wheel Size)</h3>
 *
 * {@link HashedWheelTimer} maintains a data structure called 'wheel'.
 * To put simply, a wheel is a hash table of {@link TimerTask}s whose hash
 * function is 'dead line of the task'.  The default number of ticks per wheel
 * (i.e. the size of the wheel) is 512.  You could specify a larger value
 * if you are going to schedule a lot of timeouts.
 *
 * <h3>Do not create many instances.</h3>
 *
 * {@link HashedWheelTimer} creates a new thread whenever it is instantiated and
 * started.  Therefore, you should make sure to create only one instance and
 * share it across your application.  One of the common mistakes, that makes
 * your application unresponsive, is to create a new instance for every connection.
 *
 * <h3>Implementation Details</h3>
 *
 * {@link HashedWheelTimer} is based on
 * <a href="http://cseweb.ucsd.edu/users/varghese/">George Varghese</a> and
 * Tony Lauck's paper,
 * <a href="http://cseweb.ucsd.edu/users/varghese/PAPERS/twheel.ps.Z">'Hashed
 * and Hierarchical Timing Wheels: data structures to efficiently implement a
 * timer facility'</a>.  More comprehensive slides are located
 * <a href="http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt">here</a>.
 *
 *
 * Netty主要应用于网络通信，本书完成了一套分布式RPC框架，实 现了服务之间的长连接通信。Netty还有一个非常重要的应用领域—— 即时通信系统IM。
 * 在IM聊天系统中，有成千上万甚至百万条的链路， Netty是如何管理这些链路的呢?Netty有一套自带的心跳检测机制， 这套检测机制的原理是通过创建
 * 多个定时任务ScheduledFutureTask， 定时一段时间与客户端进行通信，确保连接可用。
 *
 * 除了自带的心跳检测机制，Netty还提供了另外一种方案，叫时间 轮HashedWheelTimer。时间轮也是一个定时任务，只是这个定时任务 是额外的一条
 * 线程，每隔一段时间运行一次，在运行过程中，只会把 当前时间要执行的任务捞出来运行，而并不会去捞那些还未到时的任 务。
 *
 *
 *都知道时钟有指针、刻度、每刻度表示的时长等属性，Netty时间 轮的设计也差不多，只是时钟的指针有时、分、秒，而Netty只用了一 个指针。
 * 那么Netty是如何把定时任务加入时间轮的呢?下面先看一幅 时间轮的构造图，如图7-1所示。
 *
 *
 从图7-1中可以看出，当指针指向某一刻度时，它会把此刻度中的 所有task任务一一取出并运行。在解读Netty的时间轮代码前，先思考 以下3个问题。

 * 1. 度的间隔时间标注为tickDuration，同时将时间轮一轮的 刻度总数标注为wheelLen，两者都是时间轮的属性，可以通过构造方
 * 法由使用者传入，这样就可以得到时间轮指针走一轮的时长 =tickDuration*wheelLen。
 *
 * 2. 当指针运行到某一刻度时，需要把映射在此刻度上所有的任 务都取出来，而刻度总数在时间轮初始化后就固定了。因此与Map相 似，
 * 采用数组标识wheel[]加链表的方式来存储这些task，数组的大小 固定为图7-1中的 ，刻度的编号就是wheel[]的下标。
 *
 * 3.每个时间轮启动都会记录其启动时间，同时，每个定时任务 都有其确定的执行时间，用这个执行时间减去时间轮的启动时间，
 * 再 除以刻度的持续时长，就能获取这个定时任务需要指针走过多少刻度 才运行，标注为calculated。
 *
 * 4.时间轮本身记录了当前指针已经走过了多少刻度，标注为tick。 通过calculated、tick、时间轮刻度总数wheelLen来计算定时任务在
 * 哪一刻度执行(此刻度标注为stopIndex)。需要分为以下几种情况进 行处理。
 * 当calculated < tick  时，说明这项任务已经是旧任务了，可以立即执行，因此stopIndex = tick
 * 当(calculated-tick) <= wheelLen时，stopIndex=(calculated-tick)
 * 当(calculated-tick) > wheelLen 时，calculated肯定大于wheelLen,若wheelLen是2的整数次幂，则可以运用与运算
 * stopIndex=calculated&(wheelLen-1); 若 wheelLen 不 是 2 的 整 数 次 幂，则把它转换成距离最近的2的整数次幂即可。
 *
 *
 * 经过对以上3个问题进行的分析，对时间轮的构造有了基本的认 知，了解了时间轮内部属性特征，以及定时任务与刻度的映射关系。 但具体时间轮是如
 * 何运行的，它的指针是如何跳动的。这都需要通过 仔细阅读Netty的时间轮源码来寻找答案。时间轮源码分为两部分:第 一部分包含时间
 * 轮的核心属性、初始化构建、启动和定时检测任务的
 *添加;第二部分主要是对时间轮的时钟Worker线程的剖析。线程的核 心功能有时钟指针的刻度跳动、超时任务处理、任务的取消等。
 *
 * 时间轮HashedWheelTimer的核心属性解读如下:
 *
 *
 * 通过7.1节的学习，虽然没有实际的运用时间轮，但是对它有了比 较深入的了解。采用时间轮进行心跳检测的实现思路大概如下。
 * 每条I/O线程都会构建一个时间轮，当然也可以只构建一个静态的 时间轮，根据链路数量来决定。
 *
 * 当有Channel通道进来时，会触发channelRegistered()方法，在 此方法中，把通道的心跳定时检测任务交给时间轮，再调用其 newTimeout()方法把任务添加到时间轮中。
 *
 * 采用时间轮去执行这些定时任务，很明显可以减轻I/O线程的负 担，但这些定时任务同样是放在内存中的，因此设置定时检测时间一 定要注意不宜过长。
 * 虽然单机的长连接并发量不会太高，放在内存也 不会有太大的影响。但是若除心跳检测外，用时间轮作为公司的任务 定时调度系统或监控10亿级定时检测任务系统，
 * 则此时再放内存，恐 怕再大的内存也会被撑爆，本节通过改造时间轮来解决这个问题?
 *
 * 以监控10亿级定时检测任务系统为例，想要实现这套系统，用传 统轮询方式性能肯定无法满足要求;用时间轮来实现，若将每天10亿 级任务存放到内存中，
 * 则肯定会发生内存溢出，但可以通过改造把时 间轮的任务数据存放到其他地方，如数据库Redis、HBase等。但是若 把这些定时检测数据放入Redis中，则此时会引发以下问题。
 * 1. 这些数据的存放与时间轮刻度如何映射？
 * 2. 时间轮存储的检测数据有可能在不断的更新，在时间轮指针每走一刻时，应该如何获取最亲近的检测数据呢？
 * 3. 当时间轮服务宕机或改版重启时，在服务恢复正常后，这些定时检测数据该如何处理，若像Netty服务那样，直接把这些数据丢弃。
 *
 * 再重写一遍Redis，则skcwfc遇到数据严重阻塞，还有丢弃数据的可能，需要找到其他更好的解决办法 。
 *
 * 1. 数据的存放主要考虑获取方便，在获取时，时间轮只需要提供当前刻度编号idx,时间轮唯一标识wheel , 时间轮指针走过了多少刻度tick即可，这些数据代表了时间轮的当前dudy，若用
 * HBase来存储，则可以采用前缀扫描Scan, 若用Redis来存储，则可以考虑存放了多个 List 中，这些List 的Key 的前缀一致辞，由于 node+idx+tick组成，先从
 * Redis中根据前缀获取这些Key , 再把对应的定时检测数据捞出来 。
 *
 * 2. 通过Key可以直接获取在时间轮上映射的任务数据，但这些数据早已经不再是最新数据了，为了防止误报，需要获取最新数据，此时就需要在这些数据上设置
 *  唯一的id与最新数据的id 一致， 并把这些原始数据存入在额外的容器中，通过id及时覆盖旧的数据，也可以通过id链表批量从容器中获取最新的数据 。
 *
 * 3. 当时间轮所在的服务器宕机或重启， 在服务恢复后，只需要恢复时间轮的元素即可，包括其启动时间，指针目前移动了多少刻度，时间轮本身唯一标识
 * ，每刻度持续时间长， 指针走一轮的总刻度值，指针当前所在的刻度编号，根据这些发展重新构建时间轮，无须做任何数据的回放工作，但是要注意，时间轮指针每走
 * 一词义，就需要把时间轮当前的状态进行及时更新 。 时间轮状态信息也可能以存储到数据库中， 如MySQL , Elasticsearch,Hbase,Redis 。
 *
 *
 * 运用7.2节时间轮的改造思想，本节会完成一套一天监控10亿级时检测任务系统的架构设计，这套系统的应用范围非常广，例如，外卖平台每天处理上千万
 * 份订单，它是如何确保这些订单在合理的时间送达的呢？ 若大量外卖单出现超时未处理的问题，那么超过多久后将无法在预定的时间内送达而收到用户的投诉
 * 若有一套报警系统，当用户下单后，在规定的时间内无任何处理时，能及时给相关的人员发送告警信息， 操作员收到信息后及时处理，则可以完成订单时效
 * 的达成 ， 从而减少用户投诉，这样不仅可以给公司减少赔偿， 还对公司的品牌形象有很大的影响 。
 *
 * 虽然已经有了明确的改造方案，但由于系统吞吐量平均一分钟要处理上百万条数据，所以在编译开始前，需要进行架构设计，评审，在架构设计前，还需要进行
 * 技术选型 ， 技术选型需要考虑以下几点 。
 * 1. 采用合适的组件作为实时处理数据源。
 * 2. 时间轮计算嗠需要在公布式组件上运行。
 * 3. 数据存储除了Redis ，其他数据库是否也能支持。
 * 4. 最终结果数据的输出方案。
 *
 *
 * 1. 由于数据量庞大，而且要时刻监控数据是否被及时处理，因此数据源选择Kafka比较合适，当数据上智下愚能力弱时，数据留在Kafka中不会丢失，对数据生产
 * 也不会造成影响，而且其扩展性极强，性能也会非常不错。
 *
 * 2. 分页式实时流计算框架可以选择Flink或JStorm ，它们都具有分页式动态扩展，能接入Kafka数据实时流处理，吞吐量高等特点，Flink不需要对每
 * 条消息做ACK ,与JStorm相比，它的吞吐量上会更高，有checkpoint机制，当checkpoint成功时，代表之前的数据都成功消费了， Kafka消费组对
 * 各个分区的消费的偏移量会进行相应的更新 。
 *
 * 3. 数据库除了Redis ， 还有HBase ,  只是Hbase 的批量GET偶尔会出现有些数据获取不到的情况，在region分裂时可能会抛出异常。 本书只提供了一
 * 一个使用HBase 的思路，当使用HBase  时，任凭数据的rowkey前缀与Redis 的链表key前缀类似，由 wheel + idx + tick 组成 ， 当时间轮每
 * 走一格时，可以通过rowKey前缀循环Scan把对应的格式中的都捞出来并进行处理。
 *
 * 4. 结果输出有两种方案，第一种方案， （也是最便捷的方案）是执行Kafka ，第二种方案是通过配置一个URL http 请求地址回调给对方，虽然异常
 * 数据不会太多，但为了方便，推荐使用了Kafka方案，感兴趣的话可以两者都用。
 *
 * 监控10亿级定时检测任务系统的架构设计图如图7-2所示，图中有 多个时间轮，每个时间轮由常量+Flink的任务id组成，由于系统处理 的数据量非常大，
 * 因此需要两套分布式实时处理程序:第一套加上时 间轮，用于任务数据在时间轮上的映射和存储;第二套接收时间轮指 针每跳一格发出的消息，根据这些
 * 消息从Redis数据库中捞取对应格子 的任务数据并进行计算，进而输出最终结果。系统可横向无限扩展， 整个系统性能瓶颈在Redis大批量接收请求上。
 *
 *
 *
 */
public class HashedWheelTimer implements Timer {

    static final InternalLogger logger =
            InternalLoggerFactory.getInstance(HashedWheelTimer.class);

    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();   //时间轮的实例个数
    private static final AtomicBoolean WARNED_TOO_MANY_INSTANCES = new AtomicBoolean(); // 在服务过程中，时间轮实例个数不能超过64个
    private static final int INSTANCE_COUNT_LIMIT = 64;
    private static final long MILLISECOND_NANOS = TimeUnit.MILLISECONDS.toNanos(1);     // 刻度持续时最小值，不能小于这个最小值
    private static final ResourceLeakDetector<HashedWheelTimer> leakDetector =
            ResourceLeakDetectorFactory.instance()

            .newResourceLeakDetector(HashedWheelTimer.class, 1);  // 内存泄漏检测

    private static final AtomicIntegerFieldUpdater<HashedWheelTimer> WORKER_STATE_UPDATER =
            // 原子性更新时间轮工作状态，防止多线程重复操作

            AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimer.class, "workerState");

    private final ResourceLeakTracker<HashedWheelTimer> leak;                   // 内存泄漏检测虚引用
    private final Worker worker = new Worker();                         // 用于构建时间轮工作线程的Runnable掌控指针的跳动
    private final Thread workerThread;                                      // 时间轮工作线程
    // 时间轮的3种工作状态分别为初始化，已经启动正在运行，停止
    public static final int WORKER_STATE_INIT = 0;
    public static final int WORKER_STATE_STARTED = 1;
    public static final int WORKER_STATE_SHUTDOWN = 2;
    @SuppressWarnings({ "unused", "FieldMayBeFinal" })
    private volatile int workerState; // 0 - init, 1 - started, 2 - shut down

    private final long tickDuration;                    // 每刻度的持续时间
    // 此数组用于存储映射在时间轮刻度上的
    private final HashedWheelBucket[] wheel;

    private final int mask;                                         // 时间轮总格子数 -1
    private final CountDownLatch startTimeInitialized = new CountDownLatch(1); // 同步计数器，时间轮Workder 线程启动后，将同步给调用时间轮的线程

    // 超时task任务队列，先将任务放入到这个队列中， 再在Worker 线程中队列中取出并放入wheel[]的链表中
    private final Queue<HashedWheelTimeout> timeouts = PlatformDependent.newMpscQueue();

    // 取消的task任务存放队列，在Worker线程中会检测是否有任务需要取消 ， 若有，则找到对应的链表，并修改这些取消任务的前后任务的指针
    private final Queue<HashedWheelTimeout> cancelledTimeouts = PlatformDependent.newMpscQueue();
    private final AtomicLong pendingTimeouts = new AtomicLong(0);
    private final long maxPendingTimeouts;              // 时间轮最多容纳多少定时检测任务，默认为-1，无限制

    private volatile long startTime;                            // 时间轮启动时间

    /**
     * Creates a new timer with the default thread factory
     * ({@link Executors#defaultThreadFactory()}), default tick duration, and
     * default number of ticks per wheel.
     */
    public HashedWheelTimer() {
        this(Executors.defaultThreadFactory());
    }

    /**
     * Creates a new timer with the default thread factory
     * ({@link Executors#defaultThreadFactory()}) and default number of ticks
     * per wheel.
     *
     * @param tickDuration   the duration between tick
     * @param unit           the time unit of the {@code tickDuration}
     * @throws NullPointerException     if {@code unit} is {@code null}
     * @throws IllegalArgumentException if {@code tickDuration} is &lt;= 0
     */
    public HashedWheelTimer(long tickDuration, TimeUnit unit) {
        this(Executors.defaultThreadFactory(), tickDuration, unit);
    }

    /**
     * Creates a new timer with the default thread factory
     * ({@link Executors#defaultThreadFactory()}).
     *
     * @param tickDuration   the duration between tick
     * @param unit           the time unit of the {@code tickDuration}
     * @param ticksPerWheel  the size of the wheel
     * @throws NullPointerException     if {@code unit} is {@code null}
     * @throws IllegalArgumentException if either of {@code tickDuration} and {@code ticksPerWheel} is &lt;= 0
     */
    public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(Executors.defaultThreadFactory(), tickDuration, unit, ticksPerWheel);
    }

    /**
     * Creates a new timer with the default tick duration and default number of
     * ticks per wheel.
     *
     * @param threadFactory  a {@link ThreadFactory} that creates a
     *                       background {@link Thread} which is dedicated to
     *                       {@link TimerTask} execution.
     * @throws NullPointerException if {@code threadFactory} is {@code null}
     */
    public HashedWheelTimer(ThreadFactory threadFactory) {
        this(threadFactory, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a new timer with the default number of ticks per wheel.
     *
     * @param threadFactory  a {@link ThreadFactory} that creates a
     *                       background {@link Thread} which is dedicated to
     *                       {@link TimerTask} execution.
     * @param tickDuration   the duration between tick
     * @param unit           the time unit of the {@code tickDuration}
     * @throws NullPointerException     if either of {@code threadFactory} and {@code unit} is {@code null}
     * @throws IllegalArgumentException if {@code tickDuration} is &lt;= 0
     */
    public HashedWheelTimer(
            ThreadFactory threadFactory, long tickDuration, TimeUnit unit) {
        this(threadFactory, tickDuration, unit, 512);
    }

    /**
     * Creates a new timer.
     *
     * @param threadFactory  a {@link ThreadFactory} that creates a
     *                       background {@link Thread} which is dedicated to
     *                       {@link TimerTask} execution.
     * @param tickDuration   the duration between tick
     * @param unit           the time unit of the {@code tickDuration}
     * @param ticksPerWheel  the size of the wheel
     * @throws NullPointerException     if either of {@code threadFactory} and {@code unit} is {@code null}
     * @throws IllegalArgumentException if either of {@code tickDuration} and {@code ticksPerWheel} is &lt;= 0
     */
    public HashedWheelTimer(
            ThreadFactory threadFactory,
            long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(threadFactory, tickDuration, unit, ticksPerWheel, true);
    }

    /**
     * Creates a new timer.
     *
     * @param threadFactory        a {@link ThreadFactory} that creates a
     *                             background {@link Thread} which is dedicated to
     *                             {@link TimerTask} execution.
     * @param tickDuration         the duration between tick
     * @param unit                 the time unit of the {@code tickDuration}
     * @param ticksPerWheel        the size of the wheel
     * @param leakDetection        {@code true} if leak detection should be enabled always,
     *                             if false it will only be enabled if the worker thread is not
     *                             a daemon thread.
     * @throws NullPointerException     if either of {@code threadFactory} and {@code unit} is {@code null}
     * @throws IllegalArgumentException if either of {@code tickDuration} and {@code ticksPerWheel} is &lt;= 0
     */


    public HashedWheelTimer(
            ThreadFactory threadFactory,
        long tickDuration, TimeUnit unit, int ticksPerWheel, boolean leakDetection) {
        this(threadFactory, tickDuration, unit, ticksPerWheel, leakDetection, -1);
    }


    /**
     * Creates a new timer.
     *
     * @param threadFactory        a {@link ThreadFactory} that creates a
     *                             background {@link Thread} which is dedicated to
     *                             {@link TimerTask} execution.
     * @param tickDuration         the duration between tick
     * @param unit                 the time unit of the {@code tickDuration}
     * @param ticksPerWheel        the size of the wheel
     * @param leakDetection        {@code true} if leak detection should be enabled always,
     *                             if false it will only be enabled if the worker thread is not
     *                             a daemon thread.
     * @param  maxPendingTimeouts  The maximum number of pending timeouts after which call to
     *                             {@code newTimeout} will result in
     *                             {@link java.util.concurrent.RejectedExecutionException}
     *                             being thrown. No maximum pending timeouts limit is assumed if
     *                             this value is 0 or negative.
     * @throws NullPointerException     if either of {@code threadFactory} and {@code unit} is {@code null}
     * @throws IllegalArgumentException if either of {@code tickDuration} and {@code ticksPerWheel} is &lt;= 0
     */
    /**
     *  时间轮构造函数
     * @param threadFactory  线程工厂，用于创建线程
     * @param tickDuration              刻度持续时长
     * @param unit                  刻度持续时长单位
     * @param ticksPerWheel             时间轮总刻度数
     * @param leakDetection         是否开启内存泄漏检测
     * @param maxPendingTimeouts            时间轮可接受最大定时检测任务数
     */
    public HashedWheelTimer(
            ThreadFactory threadFactory,
            long tickDuration, TimeUnit unit, int ticksPerWheel, boolean leakDetection,
            long maxPendingTimeouts) {

        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (tickDuration <= 0) {
            throw new IllegalArgumentException("tickDuration must be greater than 0: " + tickDuration);
        }
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }

        // Normalize ticksPerWheel to power of two and initialize the wheel.   对时间轮刻度数进行格式化，转换成高ticksPerWheel最近的2的整数次幂，并初始化wheel 数组
        wheel = createWheel(ticksPerWheel);
        mask = wheel.length - 1;

        // Convert tickDuration to nanos.  把刻度持续时长转换成纳秒，这样更加精确
        long duration = unit.toNanos(tickDuration);

        // Prevent overflow.                检测持续时长不能太长，但也不能太短
        if (duration >= Long.MAX_VALUE / wheel.length) {
            throw new IllegalArgumentException(String.format(
                    "tickDuration: %d (expected: 0 < tickDuration in nanos < %d",
                    tickDuration, Long.MAX_VALUE / wheel.length));
        }

        if (duration < MILLISECOND_NANOS) {
            if (logger.isWarnEnabled()) {
                logger.warn("Configured tickDuration %d smaller then %d, using 1ms.",
                            tickDuration, MILLISECOND_NANOS);
            }
            this.tickDuration = MILLISECOND_NANOS;
        } else {
            this.tickDuration = duration;
        }

        workerThread = threadFactory.newThread(worker);                     // 构建时间轮的Worker线程

        leak = leakDetection || !workerThread.isDaemon() ? leakDetector.track(this) : null;         // 是否需要内存泄漏检测

        this.maxPendingTimeouts = maxPendingTimeouts;                  // 最大定时检测任务个数

        if (INSTANCE_COUNTER.incrementAndGet() > INSTANCE_COUNT_LIMIT &&                // 时间轮实例个数检测，超过64个会告警

            WARNED_TOO_MANY_INSTANCES.compareAndSet(false, true)) {
            reportTooManyInstances();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            // This object is going to be GCed and it is assumed the ship has sailed to do a proper shutdown. If
            // we have not yet shutdown then we want to make sure we decrement the active instance count.
            if (WORKER_STATE_UPDATER.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                INSTANCE_COUNTER.decrementAndGet();
            }
        }
    }

    /**
     * 格式化总刻度数，初始化时间轮容器
     * @param ticksPerWheel
     * @return
     */
    private static HashedWheelBucket[] createWheel(int ticksPerWheel) {
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException(
                    "ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }
        if (ticksPerWheel > 1073741824) {
            throw new IllegalArgumentException(
                    "ticksPerWheel may not be greater than 2^30: " + ticksPerWheel);
        }

        ticksPerWheel = normalizeTicksPerWheel(ticksPerWheel);          // 格式化
        HashedWheelBucket[] wheel = new HashedWheelBucket[ticksPerWheel];
        for (int i = 0; i < wheel.length; i ++) {
            wheel[i] = new HashedWheelBucket();
        }
        return wheel;
    }


    // 找到离ticksPerWheel最近的2个整数次幂
    private static int normalizeTicksPerWheel(int ticksPerWheel) {
        int normalizedTicksPerWheel = 1;
        while (normalizedTicksPerWheel < ticksPerWheel) {
            normalizedTicksPerWheel <<= 1;
        }
        return normalizedTicksPerWheel;
    }

    /**
     * Starts the background thread explicitly.  The background thread will
     * start automatically on demand even if you did not call this method.
     *
     * @throws IllegalStateException if this timer has been
     *                               {@linkplain #stop() stopped} already
     */
    public void start() {                               // 时间轮启动
        switch (WORKER_STATE_UPDATER.get(this)) {                  // 根据时间轮状态进行对应的处理
            case WORKER_STATE_INIT:                            // 当时间轮处于初始化状态时，需要启动它
                if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_INIT, WORKER_STATE_STARTED)) {        // 原子性启动
                    workerThread.start();
                }
                break;
            case WORKER_STATE_STARTED:
                break;
            case WORKER_STATE_SHUTDOWN:
                throw new IllegalStateException("cannot be started once stopped");
            default:
                throw new Error("Invalid WorkerState");
        }

        // Wait until the startTime is initialized by the worker.
        while (startTime == 0) {                            // 等待Worker 线程初始化成功
            try {
                startTimeInitialized.await();
            } catch (InterruptedException ignore) {
                // Ignore - it will be ready very soon.
            }
        }
    }

    @Override
    public Set<Timeout> stop() {
        if (Thread.currentThread() == workerThread) {
            throw new IllegalStateException(
                    HashedWheelTimer.class.getSimpleName() +
                            ".stop() cannot be called from " +
                            TimerTask.class.getSimpleName());
        }

        if (!WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_STARTED, WORKER_STATE_SHUTDOWN)) {
            // workerState can be 0 or 2 at this moment - let it always be 2.
            if (WORKER_STATE_UPDATER.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                INSTANCE_COUNTER.decrementAndGet();
                if (leak != null) {
                    boolean closed = leak.close(this);
                    assert closed;
                }
            }

            return Collections.emptySet();
        }

        try {
            boolean interrupted = false;
            while (workerThread.isAlive()) {
                workerThread.interrupt();
                try {
                    workerThread.join(100);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } finally {
            INSTANCE_COUNTER.decrementAndGet();
            if (leak != null) {
                boolean closed = leak.close(this);
                assert closed;
            }
        }
        return worker.unprocessedTimeouts();
    }

    @Override
    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        long pendingTimeoutsCount = pendingTimeouts.incrementAndGet(); // 需等待执行任务数+ 1 ， 同时判断是否超过最大限制

        if (maxPendingTimeouts > 0 && pendingTimeoutsCount > maxPendingTimeouts) {
            pendingTimeouts.decrementAndGet();
            throw new RejectedExecutionException("Number of pending timeouts ("
                + pendingTimeoutsCount + ") is greater than or equal to maximum allowed pending "
                + "timeouts (" + maxPendingTimeouts + ")");
        }

        start();                            // 若时间轮Worker线程未启动，则需要启动

        // Add the timeout to the timeout queue which will be processed on the next tick.
        // During processing all the queued HashedWheelTimeouts will be added to the correct HashedWheelBucket.
        // 根据定时任务延时执行时间与时间轮启动时间，获取相对的时间轮开始后的任务执行延时时间，因为时间轮开始启动时间不是会改变的， 所以通过这个时间可以获取时钟需要跳动的刻度
        long deadline = System.nanoTime() + unit.toNanos(delay) - startTime;

        // Guard against overflow.
        if (delay > 0 && deadline < 0) {
            deadline = Long.MAX_VALUE;
        }
        // 构建定时检测任务，并将其添加到新增定时检测任务队列中， 在Worker线程中，会从队列中取出定时检测任务并发放入缓存数组wheel中
        HashedWheelTimeout timeout = new HashedWheelTimeout(this, task, deadline);
        timeouts.add(timeout);
        return timeout;
    }

    /**
     * Returns the number of pending timeouts of this {@link Timer}.
     */
    public long pendingTimeouts() {
        return pendingTimeouts.get();
    }

    private static void reportTooManyInstances() {
        if (logger.isErrorEnabled()) {
            String resourceType = simpleClassName(HashedWheelTimer.class);
            logger.error("You are creating too many " + resourceType + " instances. " +
                    resourceType + " is a shared resource that must be reused across the JVM," +
                    "so that only a few instances are created.");
        }
    }



    // Worker线程是整个时间轮的核心，它拥有一个属性——tick。 tick与时间刻度有一定的关联，指针每经过一个刻度后，tick++; tick与mask
    // (时间轮总格子数-1)进行与操作后，就是时间轮指针的 当前刻度序号。在Worker线程中，tick做了以下4件事。
    // 1. 等待下一刻度运行时间到来。
    // 2. 从取消任务队列中获取需要取消的任务并处理。
    // 3. 从任务队列中获取需要执行的定时检测任务，并把它们放入对 应的刻度链表中。
    // 4. 从当前刻度链表中取出需要执行的定时检测任务，并循环执行 这些定时检测任务的run()方法。
    private final class Worker implements Runnable {
        private final Set<Timeout> unprocessedTimeouts = new HashSet<Timeout>();  // 当调用了时间轮的stop()方法后，将获取其未执行完的任务

        private long tick;                              // 时钟指针的跳动次数

        @Override
        public void run() {
            // Initialize the startTime.
            startTime = System.nanoTime();                      // 时间轮启动的时间
            if (startTime == 0) {
                // We use 0 as an indicator for the uninitialized value here, so make sure it's not 0 when initialized.
                startTime = 1;
            }

            // Notify the other threads waiting for the initialization at start().      Worker 线程初始化了，通知调用时间轮启动的线程
            startTimeInitialized.countDown();

            do {
                // 获取下一刻度时间轮总体的执行时间，录这个时间与时间轮启动时间和大于当前时间时， 线程会睡眠到这个时间点
                final long deadline = waitForNextTick();
                if (deadline > 0) {
                    int idx = (int) (tick & mask);      // 获取刻度的编号，即wheel 数组的下标
                    processCancelledTasks();                // 先处理需要取消的任务
                    HashedWheelBucket bucket = wheel[idx];        // 获取刻度所在的缓存链表
                    transferTimeoutsToBuckets();                // 把所有的样报增加的定时任务检测任务加入wheel数组的缓存链表中
                    bucket.expireTimeouts(deadline);                        // 循环执行刻度所在的缓存链表
                    tick++;             // 执行完后，指针才正式跳动
                }
            } while (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_STARTED);      // 时间轮状态需要为已经启动状态

            // Fill the unprocessedTimeouts so we can return them from stop() method.
            for (HashedWheelBucket bucket: wheel) {                // 运行到这里说明时间轮停止了，需要把未处理的任务返回
                bucket.clearTimeouts(unprocessedTimeouts);
            }
            for (;;) {                          // 刚刚加入还未来得及放入时间轮缓存中的超时任务 ，也需要捞出并放入到unprocessedTimeouts中一起返回
                HashedWheelTimeout timeout = timeouts.poll();
                if (timeout == null) {
                    break;
                }
                if (!timeout.isCancelled()) {
                    unprocessedTimeouts.add(timeout);
                }
            }
            processCancelledTasks();              // 处理需要取消的任务
        }

        private void transferTimeoutsToBuckets() {
            // transfer only max. 100000 timeouts per tick to prevent a thread to stale the workerThread when it just
            // adds new timeouts in a loop.
            for (int i = 0; i < 100000; i++) {
                HashedWheelTimeout timeout = timeouts.poll();
                if (timeout == null) {
                    // all processed
                    break;
                }
                if (timeout.state() == HashedWheelTimeout.ST_CANCELLED) {
                    // Was cancelled in the meantime.
                    continue;
                }
                // 每个时间轮启动都会记录其启动时间，同时，每个定时任务 都有其确定的执行时间，用这个执行时间减去时间轮的启动时间，
                // 再 除以刻度的持续时长，就能获取这个定时任务需要指针走过多少刻度 才运行，标注为calculated。
                long calculated = timeout.deadline / tickDuration;
                timeout.remainingRounds = (calculated - tick) / wheel.length;

                final long ticks = Math.max(calculated, tick); // Ensure we don't schedule for past.
                int stopIndex = (int) (ticks & mask);

                HashedWheelBucket bucket = wheel[stopIndex];
                bucket.addTimeout(timeout);
            }
        }

        private void processCancelledTasks() {
            for (;;) {
                HashedWheelTimeout timeout = cancelledTimeouts.poll();
                if (timeout == null) {
                    // all processed
                    break;
                }
                try {
                    timeout.remove();
                } catch (Throwable t) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("An exception was thrown while process a cancellation task", t);
                    }
                }
            }
        }

        /**
         * calculate goal nanoTime from startTime and current tick number,
         * then wait until that goal has been reached.
         * @return Long.MIN_VALUE if received a shutdown request,
         * current time otherwise (with Long.MIN_VALUE changed by +1)
         */
        private long waitForNextTick() {
            long deadline = tickDuration * (tick + 1);                      // 获取下一刻度时间轮总体的执行时间

            for (;;) {
                final long currentTime = System.nanoTime() - startTime;         // 当前时间 - 启动时间
                // 计算需要睡眠的毫秒时间 ， 由于在将纳秒frqo毫秒时需要除以1000000，因此需要加上999999，以防赴丢失尾数，任务被提前执行
                long sleepTimeMs = (deadline - currentTime + 999999) / 1000000;

                if (sleepTimeMs <= 0) {                     // 当睡眠时间小于，且 等于Long.MiN_VALUE时，直跳过此刻度，否则不睡眠，直接执行任务
                    if (currentTime == Long.MIN_VALUE) {
                        return -Long.MAX_VALUE;
                    } else {
                        return currentTime;
                    }
                }

                // Check if we run on windows, as if thats the case we will need
                // to round the sleepTime as workaround for a bug that only affect
                // the JVM if it runs on windows.
                //
                // See https://github.com/netty/netty/issues/356  Window 操作系统特殊处理， 其Sleep函数是以10ms 为单位进行延时的，
                // 也就是说，所有小于10且大于0的情况都是10ms， 所有大于 10且小于20的情况都是20ms , 因此这里做了特殊的处理， 对于小于10ms 的，直接不睡眠。 对于 大于 10ms的，去掉层尾数
                if (PlatformDependent.isWindows()) {
                    sleepTimeMs = sleepTimeMs / 10 * 10;
                }

                try {
                    Thread.sleep(sleepTimeMs);
                } catch (InterruptedException ignored) {
                    // 当发生异常，发现时间轮状态为WORKER_STATE_SHUTDOWN时，立刻返回
                    if (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_SHUTDOWN) {
                        return Long.MIN_VALUE;
                    }
                }
            }
        }

        public Set<Timeout> unprocessedTimeouts() {
            return Collections.unmodifiableSet(unprocessedTimeouts);
        }
    }

    private static final class HashedWheelTimeout implements Timeout {

        private static final int ST_INIT = 0;
        private static final int ST_CANCELLED = 1;
        private static final int ST_EXPIRED = 2;
        private static final AtomicIntegerFieldUpdater<HashedWheelTimeout> STATE_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimeout.class, "state");

        private final HashedWheelTimer timer;
        private final TimerTask task;
        private final long deadline;

        @SuppressWarnings({"unused", "FieldMayBeFinal", "RedundantFieldInitialization" })
        private volatile int state = ST_INIT;

        // remainingRounds will be calculated and set by Worker.transferTimeoutsToBuckets() before the
        // HashedWheelTimeout will be added to the correct HashedWheelBucket.
        long remainingRounds;

        // This will be used to chain timeouts in HashedWheelTimerBucket via a double-linked-list.
        // As only the workerThread will act on it there is no need for synchronization / volatile.
        HashedWheelTimeout next;
        HashedWheelTimeout prev;

        // The bucket to which the timeout was added
        HashedWheelBucket bucket;

        HashedWheelTimeout(HashedWheelTimer timer, TimerTask task, long deadline) {
            this.timer = timer;
            this.task = task;
            this.deadline = deadline;
        }

        @Override
        public Timer timer() {
            return timer;
        }

        @Override
        public TimerTask task() {
            return task;
        }

        @Override
        public boolean cancel() {
            // only update the state it will be removed from HashedWheelBucket on next tick.
            if (!compareAndSetState(ST_INIT, ST_CANCELLED)) {
                return false;
            }
            // If a task should be canceled we put this to another queue which will be processed on each tick.
            // So this means that we will have a GC latency of max. 1 tick duration which is good enough. This way
            // we can make again use of our MpscLinkedQueue and so minimize the locking / overhead as much as possible.
            timer.cancelledTimeouts.add(this);
            return true;
        }

        void remove() {
            HashedWheelBucket bucket = this.bucket;
            if (bucket != null) {
                bucket.remove(this);
            } else {
                timer.pendingTimeouts.decrementAndGet();
            }
        }

        public boolean compareAndSetState(int expected, int state) {
            return STATE_UPDATER.compareAndSet(this, expected, state);
        }

        public int state() {
            return state;
        }

        @Override
        public boolean isCancelled() {
            return state() == ST_CANCELLED;
        }

        @Override
        public boolean isExpired() {
            return state() == ST_EXPIRED;
        }

        public void expire() {
            if (!compareAndSetState(ST_INIT, ST_EXPIRED)) {
                return;
            }

            try {
                task.run(this);
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("An exception was thrown by " + TimerTask.class.getSimpleName() + '.', t);
                }
            }
        }

        @Override
        public String toString() {
            final long currentTime = System.nanoTime();
            long remaining = deadline - currentTime + timer.startTime;

            StringBuilder buf = new StringBuilder(192)
               .append(simpleClassName(this))
               .append('(')
               .append("deadline: ");
            if (remaining > 0) {
                buf.append(remaining)
                   .append(" ns later");
            } else if (remaining < 0) {
                buf.append(-remaining)
                   .append(" ns ago");
            } else {
                buf.append("now");
            }

            if (isCancelled()) {
                buf.append(", cancelled");
            }

            return buf.append(", task: ")
                      .append(task())
                      .append(')')
                      .toString();
        }
    }

    /**
     * Bucket that stores HashedWheelTimeouts. These are stored in a linked-list like datastructure to allow easy
     * removal of HashedWheelTimeouts in the middle. Also the HashedWheelTimeout act as nodes themself and so no
     * extra object creation is needed.
     */
    private static final class HashedWheelBucket {
        // Used for the linked-list datastructure
        private HashedWheelTimeout head;
        private HashedWheelTimeout tail;

        /**
         * Add {@link HashedWheelTimeout} to this bucket.
         */
        public void addTimeout(HashedWheelTimeout timeout) {
            assert timeout.bucket == null;
            timeout.bucket = this;
            if (head == null) {
                head = tail = timeout;
            } else {
                tail.next = timeout;
                timeout.prev = tail;
                tail = timeout;
            }
        }

        /**
         * Expire all {@link HashedWheelTimeout}s for the given {@code deadline}.
         */
        public void expireTimeouts(long deadline) {
            HashedWheelTimeout timeout = head;

            // process all timeouts
            while (timeout != null) {
                HashedWheelTimeout next = timeout.next;
                if (timeout.remainingRounds <= 0) {
                    next = remove(timeout);
                    if (timeout.deadline <= deadline) {
                        timeout.expire();
                    } else {
                        // The timeout was placed into a wrong slot. This should never happen.
                        throw new IllegalStateException(String.format(
                                "timeout.deadline (%d) > deadline (%d)", timeout.deadline, deadline));
                    }
                } else if (timeout.isCancelled()) {
                    next = remove(timeout);
                } else {
                    timeout.remainingRounds --;
                }
                timeout = next;
            }
        }

        public HashedWheelTimeout remove(HashedWheelTimeout timeout) {
            HashedWheelTimeout next = timeout.next;
            // remove timeout that was either processed or cancelled by updating the linked-list
            if (timeout.prev != null) {
                timeout.prev.next = next;
            }
            if (timeout.next != null) {
                timeout.next.prev = timeout.prev;
            }

            if (timeout == head) {
                // if timeout is also the tail we need to adjust the entry too
                if (timeout == tail) {
                    tail = null;
                    head = null;
                } else {
                    head = next;
                }
            } else if (timeout == tail) {
                // if the timeout is the tail modify the tail to be the prev node.
                tail = timeout.prev;
            }
            // null out prev, next and bucket to allow for GC.
            timeout.prev = null;
            timeout.next = null;
            timeout.bucket = null;
            timeout.timer.pendingTimeouts.decrementAndGet();
            return next;
        }

        /**
         * Clear this bucket and return all not expired / cancelled {@link Timeout}s.
         */
        public void clearTimeouts(Set<Timeout> set) {
            for (;;) {
                HashedWheelTimeout timeout = pollTimeout();
                if (timeout == null) {
                    return;
                }
                if (timeout.isExpired() || timeout.isCancelled()) {
                    continue;
                }
                set.add(timeout);
            }
        }

        private HashedWheelTimeout pollTimeout() {
            HashedWheelTimeout head = this.head;
            if (head == null) {
                return null;
            }
            HashedWheelTimeout next = head.next;
            if (next == null) {
                tail = this.head =  null;
            } else {
                this.head = next;
                next.prev = null;
            }

            // null out prev and next to allow for GC.
            head.next = null;
            head.prev = null;
            head.bucket = null;
            return head;
        }
    }
}
