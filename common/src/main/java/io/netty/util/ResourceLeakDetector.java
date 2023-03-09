/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.util.internal.StringUtil.EMPTY_STRING;
import static io.netty.util.internal.StringUtil.NEWLINE;
import static io.netty.util.internal.StringUtil.simpleClassName;

/**
 * Netty在默认情况下采用的是池化的PooledByteBuf，以提高程序 性能。但是PooledByteBuf在使用完毕后需要手动释放，否则会因 PooledByteBuf
 * 申请的内存空间没有归还导致内存泄漏，最终使内存溢 出。一旦泄漏发生，在复杂的应用程序中找到未释放的ByteBuf并不是 一个简单的事，在没有工具辅助的情况下只能检查所有源码，效率很 低。
 *
 * 为了解决这个问题，Netty运用JDK的弱引用和引用队列设计了一 套专门的内存泄漏检测机制，用于实现对需要手动释放的ByteBuf对象 的监控。
 *
 *
 * 图4-12描述了各种引用及其各自的特征。
 *                |---------> 强引用
 * 引用 --------> | ---------> 软引用 -------->  SoftReference --------> 当内存不足时，被丧回收器回收
 *                |
 *                |                           |------> 当回收时，纯弱引用的对象都会被回收掉
 *                | --------->  弱引用 ------> |------> 与引用队列配合使用， 当回收对象时，会将对象的弱引用加入到队列中
 *                |
 *                |                                                     |------> 任何时候都可能被垃圾回收器回收
 *                |----------> 虚引用 ---------> WeakReference---------->|------> 与引用队列配合使用，当回收对象时，会将对象的虚引用加入到队列中
 * 强引用:经常使用的编码方式，如果将一个对象赋值给一个变 量，只要这个变量可用，那么这个对象的值就被该变量强引用了;否 则垃圾回收器不会回收该对象。
 * 软引用:当内存不足时，被垃圾回收器回收，一般用于缓存。
 * 弱引用:只要是发生回收的时候，纯弱引用的对象都会被回收; 当对象未被回收时，弱引用可以获取引用的对象。
 * 虚引用:在任何时候都可能被垃圾回收器回收。如果一个对象与 虚引用关联，则该对象与没有引用与之关联时一样。虚引用获取不到引用的对象。
 *
 * 引用队列:与虚引用或弱引用配合使用，当普通对象被垃圾回收 器回收时，会将对象的弱引用和虚引用加入引用队列中。Netty运用这
 * 一特性来检测这些被回收的ByteBuf是否已经释放了内存空间。下面对 其实现原理及源码进行详细剖析。
 *
 *  4.5.1 内存泄漏检测原理
 *
 *  Netty的内存泄漏检测机制主要是检测ByteBuf的内存是否正常释 放。想要实现这种机制，就需要完成以下3步。
 *  1. 采集ByteBuf对象。
 *  2. 记录ByteBuf的最新调用轨迹信息，方便溯源。
 *  3. 检查是否有泄漏，并进行日志输出。
 *
 * 第一，采集入口在内存分配器PooledByteBufAllocator的 newDirectBuffer与newHeapBuffer方法中，对返回的ByteBuf对象做一 层 包 装 ，
 * 包 装 类 分 两 种 : SimpleLeakAwareByteBuf 与 AdvancedLeakAwareByteBuf。
 *
 * ResourceLeakDetector在整个内存泄漏检测机制中起核心作用。 一种缓冲区资源会创建一个ResourceLeakDetector实例，并监控此缓
 * 冲区类型的池化资源(本书只介绍AbstractByteBuf类型的资源)。 ResourceLeakDetector的trace()方法是整套检测机制的入口，提供资
 * 源采集逻辑，运用全局的引用队列和引用缓存Set构建ByteBuf的弱引 用对象，并检测当前监控的资源是否出现了内存泄漏。若出现了内存 泄漏，
 * 则输出泄漏报告及内存调用轨迹信息。
 *
 *
 *
 *
 * 当系统处于开发和功能测试阶段时，一般会把级别设置为 PARANOID，容易发现问题。在系统正式上线后，会把级别降到 SIMPLE。若出现了泄漏日志的情况，
 * 则在重启服务时，可以把级别调 为ADVANCED，查找内存泄漏的轨迹，方便定位。当系统上线很长一段 时间后，比较稳定了，可以禁用内存泄漏检测机制。
 * Netty对这些级别 的处理具体是怎样实现按一定比例采集的呢?通过接下来的源码解读 来寻找答案。图4-13为内存泄漏检测机制的功能。
 *
 *
 *
 */
public class ResourceLeakDetector<T> {

    private static final String PROP_LEVEL_OLD = "io.netty.leakDetectionLevel"; //内存泄露检测级别 ,SIMPLE
    private static final String PROP_LEVEL = "io.netty.leakDetection.level";
    private static final Level DEFAULT_LEVEL = Level.SIMPLE;

    private static final String PROP_TARGET_RECORDS = "io.netty.leakDetection.targetRecords";
    private static final int DEFAULT_TARGET_RECORDS = 4;

    private static final String PROP_SAMPLING_INTERVAL = "io.netty.leakDetection.samplingInterval";
    // There is a minor performance benefit in TLR if this is a power of 2.
    private static final int DEFAULT_SAMPLING_INTERVAL = 128;

    private static final int TARGET_RECORDS;
    static final int SAMPLING_INTERVAL;

    /**
     * Represents the level of resource leak detection.
     * Netty的内存泄漏检测机制有以下4种检测级别。
     */
    public enum Level {
        /**
         * Disables resource leak detection.
         */
        DISABLED,//DISABLED:表示禁用，不开启检测。
        /**
         * Enables simplistic sampling resource leak detection which reports there is a leak or not,
         * at the cost of small overhead (default).
         */
        // SIMPLE:Netty的默认设置，表示按一定比例采集。若采集的 ByteBuf出现泄漏，则打印LEAK:XXX等日志，但没有ByteBuf的任何调
        // 用栈信息输出，因为它使用的包装类是SimpleLeakAwareByteBuf，不 会进行记录。
        SIMPLE,
        /**
         * Enables advanced sampling resource leak detection which reports where the leaked object was accessed
         * recently at the cost of high overhead.
         *
         */
        // ADVANCED:它的采集与SIMPLE级别的采集一样，但会输出 ByteBuf 的 调 用 栈 信 息 ， 因 为 它 使 用 的 包 装 类 是 AdvancedLeakAwareByteBuf。
        ADVANCED,
        /**
         * Enables paranoid resource leak detection which reports where the leaked object was accessed recently,
         * at the cost of the highest possible overhead (for testing purposes only).
         */
        // PARANOID:偏执级别，这种级别在ADVANCED的基础上按100%的 比例采集。
        PARANOID;

        /**
         * Returns level based on string value. Accepts also string that represents ordinal number of enum.
         *
         * @param levelStr - level string : DISABLED, SIMPLE, ADVANCED, PARANOID. Ignores case.
         * @return corresponding level or SIMPLE level in case of no match.
         */
        static Level parseLevel(String levelStr) {
            String trimmedLevelStr = levelStr.trim();
            for (Level l : values()) {
                if (trimmedLevelStr.equalsIgnoreCase(l.name()) || trimmedLevelStr.equals(String.valueOf(l.ordinal()))) {
                    return l;
                }
            }
            return DEFAULT_LEVEL;
        }
    }

    private static Level level;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ResourceLeakDetector.class);

    static {
        final boolean disabled;
        if (SystemPropertyUtil.get("io.netty.noResourceLeakDetection") != null) {
            disabled = SystemPropertyUtil.getBoolean("io.netty.noResourceLeakDetection", false);
            logger.debug("-Dio.netty.noResourceLeakDetection: {}", disabled);
            logger.warn(
                    "-Dio.netty.noResourceLeakDetection is deprecated. Use '-D{}={}' instead.",
                    PROP_LEVEL, DEFAULT_LEVEL.name().toLowerCase());
        } else {
            disabled = false;
        }

        Level defaultLevel = disabled? Level.DISABLED : DEFAULT_LEVEL;

        // First read old property name
        String levelStr = SystemPropertyUtil.get(PROP_LEVEL_OLD, defaultLevel.name());

        // If new property name is present, use it
        levelStr = SystemPropertyUtil.get(PROP_LEVEL, levelStr);
        Level level = Level.parseLevel(levelStr);

        TARGET_RECORDS = SystemPropertyUtil.getInt(PROP_TARGET_RECORDS, DEFAULT_TARGET_RECORDS);
        SAMPLING_INTERVAL = SystemPropertyUtil.getInt(PROP_SAMPLING_INTERVAL, DEFAULT_SAMPLING_INTERVAL);

        ResourceLeakDetector.level = level;
        if (logger.isDebugEnabled()) {
            logger.debug("-D{}: {}", PROP_LEVEL, level.name().toLowerCase());
            logger.debug("-D{}: {}", PROP_TARGET_RECORDS, TARGET_RECORDS);
        }
    }

    /**
     * @deprecated Use {@link #setLevel(Level)} instead.
     */
    @Deprecated
    public static void setEnabled(boolean enabled) {
        setLevel(enabled? Level.SIMPLE : Level.DISABLED);
    }

    /**
     * Returns {@code true} if resource leak detection is enabled.
     */
    public static boolean isEnabled() {
        return getLevel().ordinal() > Level.DISABLED.ordinal();
    }

    /**
     * Sets the resource leak detection level.
     */
    public static void setLevel(Level level) {
        if (level == null) {
            throw new NullPointerException("level");
        }
        ResourceLeakDetector.level = level;
    }

    /**
     * Returns the current resource leak detection level.
     */
    public static Level getLevel() {
        return level;
    }

    /** the collection of active resources */
    private final Set<DefaultResourceLeak<?>> allLeaks =
            Collections.newSetFromMap(new ConcurrentHashMap<DefaultResourceLeak<?>, Boolean>());

    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<Object>();
    private final ConcurrentMap<String, Boolean> reportedLeaks = PlatformDependent.newConcurrentHashMap();

    private final String resourceType;
    private final int samplingInterval;

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     */
    @Deprecated
    public ResourceLeakDetector(Class<?> resourceType) {
        this(simpleClassName(resourceType));
    }

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     */
    @Deprecated
    public ResourceLeakDetector(String resourceType) {
        this(resourceType, DEFAULT_SAMPLING_INTERVAL, Long.MAX_VALUE);
    }

    /**
     * @deprecated Use {@link ResourceLeakDetector#ResourceLeakDetector(Class, int)}.
     * <p>
     * This should not be used directly by users of {@link ResourceLeakDetector}.
     * Please use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class)}
     * or {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}
     *
     * @param maxActive This is deprecated and will be ignored.
     */
    @Deprecated
    public ResourceLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
        this(resourceType, samplingInterval);
    }

    /**
     * This should not be used directly by users of {@link ResourceLeakDetector}.
     * Please use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class)}
     * or {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}
     */
    @SuppressWarnings("deprecation")
    public ResourceLeakDetector(Class<?> resourceType, int samplingInterval) {
        this(simpleClassName(resourceType), samplingInterval, Long.MAX_VALUE);
    }

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     * <p>
     * @param maxActive This is deprecated and will be ignored.
     */
    @Deprecated
    public ResourceLeakDetector(String resourceType, int samplingInterval, long maxActive) {
        if (resourceType == null) {
            throw new NullPointerException("resourceType");
        }

        this.resourceType = resourceType;
        this.samplingInterval = samplingInterval;
    }

    /**
     * Creates a new {@link ResourceLeak} which is expected to be closed via {@link ResourceLeak#close()} when the
     * related resource is deallocated.
     *
     * @return the {@link ResourceLeak} or {@code null}
     * @deprecated use {@link #track(Object)}
     */
    @Deprecated
    public final ResourceLeak open(T obj) {
        return track0(obj);
    }

    /**
     * Creates a new {@link ResourceLeakTracker} which is expected to be closed via
     * {@link ResourceLeakTracker#close(Object)} when the related resource is deallocated.
     *
     * @return the {@link ResourceLeakTracker} or {@code null}
     */
    @SuppressWarnings("unchecked")
    // 重点关注内存泄漏检测器的track()方法，此方法不仅采集buf， 还会在采集完后，检测是否有内存泄漏的buf，并打印日志。具体代码 解读如下:
    public final ResourceLeakTracker<T> track(T obj) {
        return track0(obj);
    }

    @SuppressWarnings("unchecked")
    private DefaultResourceLeak track0(T obj) {
        // 获取内存泄漏检测级别
        Level level = ResourceLeakDetector.level;
        // 不检测，也不采集
        if (level == Level.DISABLED) {
            return null;
        }

        // 当级别比偏执级别低时，获取一个128以内的随机数， 若得到的数不为0，则不采集，若为0，则检测是否有泄漏，并输出泄漏日志，同时创建一个弱引用
        if (level.ordinal() < Level.PARANOID.ordinal()) {
            if ((PlatformDependent.threadLocalRandom().nextInt(samplingInterval)) == 0) {
                reportLeak();
                return new DefaultResourceLeak(obj, refQueue, allLeaks);
            }
            return null;
        }
        // 偏执级别都采集
        reportLeak();
        return new DefaultResourceLeak(obj, refQueue, allLeaks);
    }

    private void clearRefQueue() {
        for (;;) {
            @SuppressWarnings("unchecked")
            DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
            if (ref == null) {
                break;
            }

            ref.dispose();
        }
    }

    private void reportLeak() {
        if (!logger.isErrorEnabled()) {
            clearRefQueue();
            return;
        }

        // Detect and report previous leaks.
        // 循环获取引用队列中的弱引用
        for (;;) {
            @SuppressWarnings("unchecked")
            DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
            if (ref == null) {
                break;
            }

            // 检测是否泄漏 ， 若未泄漏，则继续下一次循环
            if (!ref.dispose()) {
                continue;
            }

            // 获取buf的调用栈信息
            String records = ref.toString();
            // 不再输出曾经输出过的泄漏记录
            if (reportedLeaks.putIfAbsent(records, Boolean.TRUE) == null) {
                if (records.isEmpty()) {
                    reportUntracedLeak(resourceType);
                } else {
                    // 输出内存泄漏日志及其调用栈信息
                    reportTracedLeak(resourceType, records);
                }
            }
        }
    }

    /**
     * This method is called when a traced leak is detected. It can be overridden for tracking how many times leaks
     * have been detected.
     */
    protected void reportTracedLeak(String resourceType, String records) {
        logger.error(
                "LEAK: {}.release() was not called before it's garbage-collected. " +
                "See https://netty.io/wiki/reference-counted-objects.html for more information.{}",
                resourceType, records);
    }

    /**
     * This method is called when an untraced leak is detected. It can be overridden for tracking how many times leaks
     * have been detected.
     */
    protected void reportUntracedLeak(String resourceType) {
        logger.error("LEAK: {}.release() was not called before it's garbage-collected. " +
                "Enable advanced leak reporting to find out where the leak occurred. " +
                "To enable advanced leak reporting, " +
                "specify the JVM option '-D{}={}' or call {}.setLevel() " +
                "See https://netty.io/wiki/reference-counted-objects.html for more information.",
                resourceType, PROP_LEVEL, Level.ADVANCED.name().toLowerCase(), simpleClassName(this));
    }

    /**
     * @deprecated This method will no longer be invoked by {@link ResourceLeakDetector}.
     */
    @Deprecated
    protected void reportInstancesLeak(String resourceType) {
    }

    @SuppressWarnings("deprecation")
    // ResourceLeakDetector中有个私有类——DefaultResourceLeak， 实现了ResourceLeakTracker接口，主要负责跟踪资源的最近调用轨 迹，
    // 同时继承WeakReference弱引用。调用轨迹的记录被加入 DefaultResourceLeak的Record链表中，Record链表不会保存所有记 录，因为它的长度有一定的限制。
    private static final class DefaultResourceLeak<T>
            extends WeakReference<Object> implements ResourceLeakTracker<T>, ResourceLeak {

        @SuppressWarnings("unchecked") // generics and updaters do not mix.
        private static final AtomicReferenceFieldUpdater<DefaultResourceLeak<?>, Record> headUpdater =
                (AtomicReferenceFieldUpdater)
                        AtomicReferenceFieldUpdater.newUpdater(DefaultResourceLeak.class, Record.class, "head");

        @SuppressWarnings("unchecked") // generics and updaters do not mix.
        private static final AtomicIntegerFieldUpdater<DefaultResourceLeak<?>> droppedRecordsUpdater =
                (AtomicIntegerFieldUpdater)
                        AtomicIntegerFieldUpdater.newUpdater(DefaultResourceLeak.class, "droppedRecords");

        @SuppressWarnings("unused")
        private volatile Record head;
        @SuppressWarnings("unused")
        private volatile int droppedRecords;

        private final Set<DefaultResourceLeak<?>> allLeaks;
        private final int trackedHash;

        DefaultResourceLeak(
                Object referent,
                ReferenceQueue<Object> refQueue,
                Set<DefaultResourceLeak<?>> allLeaks) {
            super(referent, refQueue);

            assert referent != null;

            // Store the hash of the tracked object to later assert it in the close(...) method.
            // It's important that we not store a reference to the referent as this would disallow it from
            // be collected via the WeakReference.
            trackedHash = System.identityHashCode(referent);
            allLeaks.add(this);
            // Create a new Record so we always have the creation stacktrace included.
            headUpdater.set(this, new Record(Record.BOTTOM));
            this.allLeaks = allLeaks;
        }

        @Override
        public void record() {
            record0(null);
        }

        @Override
        public void record(Object hint) {
            record0(hint);
        }

        /**
         * This method works by exponentially backing off as more records are present in the stack. Each record has a
         * 1 / 2^n chance of dropping the top most record and replacing it with itself. This has a number of convenient
         * properties:
         *
         * <ol>
         * <li>  The current record is always recorded. This is due to the compare and swap dropping the top most
         *       record, rather than the to-be-pushed record.
         * <li>  The very last access will always be recorded. This comes as a property of 1.
         * <li>  It is possible to retain more records than the target, based upon the probability distribution.
         * <li>  It is easy to keep a precise record of the number of elements in the stack, since each element has to
         *     know how tall the stack is.
         * </ol>
         *
         * In this particular implementation, there are also some advantages. A thread local random is used to decide
         * if something should be recorded. This means that if there is a deterministic access pattern, it is now
         * possible to see what other accesses occur, rather than always dropping them. Second, after
         * {@link #TARGET_RECORDS} accesses, backoff occurs. This matches typical access patterns,
         * where there are either a high number of accesses (i.e. a cached buffer), or low (an ephemeral buffer), but
         * not many in between.
         *
         * The use of atomics avoids serializing a high number of accesses, when most of the records will be thrown
         * away. High contention only happens when there are very few existing records, which is only likely when the
         * object isn't shared! If this is a problem, the loop can be aborted and the record dropped, because another
         * thread won the race.
         *
         * 在ADVANCED之上的级别的操作中，ByteBuf的每项操作都涉及线程 调用栈轨迹的记录。那么该如何获取线程栈调用信息呢?在记录某个
         * 点的调用栈信息时，Netty会创建一个Record对象，Record类继承 Exception的父类Throwable。因此在创建Record对象时，当前线程的
         * 调用栈信息就会被保存起来。关于调用栈信息的保存及获取的代码解 读如下:
         *
         *  记录调用轨迹
         */
        private void record0(Object hint) {
            // Check TARGET_RECORDS > 0 here to avoid similar check before remove from and add to lastRecords
            // 如果 TARGET_RECORDS > 0 则记录
            if (TARGET_RECORDS > 0) {
                Record oldHead;
                Record prevHead;
                Record newHead;
                boolean dropped;
                do {
                    // 判断记录链头是否为空，为空表示已经关闭，把之前的链头作为第二个元素赋值给新链表
                    if ((prevHead = oldHead = headUpdater.get(this)) == null) {
                        // already closed.
                        return;
                    }
                    // 获取链表的长度
                    final int numElements = oldHead.pos + 1;
                    if (numElements >= TARGET_RECORDS) {
                        // backOffFactor 是用来计算是否替换的因子 ， 其最小值为numElements-TARGET_RECORDS ,元素越多，其值越大，最大值为30
                        final int backOffFactor = Math.min(numElements - TARGET_RECORDS, 30);
                        // 1/2^backOffFactor的概率不会执行此if 代码块， prevHead = oldHead.next 表示用之前的链头元素作为新链表的第二个元素
                        // 丢弃原来的链头， 同时设置 drapped 为false
                        if (dropped = PlatformDependent.threadLocalRandom().nextInt(1 << backOffFactor) != 0) {
                            prevHead = oldHead.next;
                        }
                    } else {
                        dropped = false;
                    }
                    // 创建一个新的Record ，并将其添加到链表上， 作为链表的新的头部
                    newHead = hint != null ? new Record(prevHead, hint) : new Record(prevHead);
                } while (!headUpdater.compareAndSet(this, oldHead, newHead));
                // 若有丢弃，则更新记录丢弃的数
                if (dropped) {
                    droppedRecordsUpdater.incrementAndGet(this);
                }
            }
        }

        // 判断是否泄漏
        boolean dispose() {
            // 清理对资源对象的引用
            clear();
            // 若引用缓存还存在此引用，则说明buf未释放，内存泄漏了
            return allLeaks.remove(this);
        }

        @Override
        public boolean close() {
            if (allLeaks.remove(this)) {
                // Call clear so the reference is not even enqueued.
                clear();
                headUpdater.set(this, null);
                return true;
            }
            return false;
        }

        @Override
        public boolean close(T trackedObject) {
            // Ensure that the object that was tracked is the same as the one that was passed to close(...).
            assert trackedHash == System.identityHashCode(trackedObject);

            try {
                return close();
            } finally {
                // This method will do `synchronized(trackedObject)` and we should be sure this will not cause deadlock.
                // It should not, because somewhere up the callstack should be a (successful) `trackedObject.release`,
                // therefore it is unreasonable that anyone else, anywhere, is holding a lock on the trackedObject.
                // (Unreasonable but possible, unfortunately.)
                reachabilityFence0(trackedObject);
            }
        }

         /**
         * Ensures that the object referenced by the given reference remains
         * <a href="package-summary.html#reachability"><em>strongly reachable</em></a>,
         * regardless of any prior actions of the program that might otherwise cause
         * the object to become unreachable; thus, the referenced object is not
         * reclaimable by garbage collection at least until after the invocation of
         * this method.
         *
         * <p> Recent versions of the JDK have a nasty habit of prematurely deciding objects are unreachable.
         * see: https://stackoverflow.com/questions/26642153/finalize-called-on-strongly-reachable-object-in-java-8
         * The Java 9 method Reference.reachabilityFence offers a solution to this problem.
         *
         * <p> This method is always implemented as a synchronization on {@code ref}, not as
         * {@code Reference.reachabilityFence} for consistency across platforms and to allow building on JDK 6-8.
         * <b>It is the caller's responsibility to ensure that this synchronization will not cause deadlock.</b>
         *
         * @param ref the reference. If {@code null}, this method has no effect.
         * @see java.lang.ref.Reference# reachabilityFence
         */
        private static void reachabilityFence0(Object ref) {
            if (ref != null) {
                synchronized (ref) {
                    // Empty synchronized is ok: https://stackoverflow.com/a/31933260/1151521
                }
            }
        }

        @Override
        // 弱引用重写了toString()方法， 需要注意，若采用IDE工具debug 调试代码则在处理对象时，IDE 会自动调用toString()方法
        public String toString() {
            // 获取记录列表的头部
            Record oldHead = headUpdater.getAndSet(this, null);
            // 若无记录，则返回空字符串
            if (oldHead == null) {
                // Already closed
                return EMPTY_STRING;
            }

            // 若记录太长，则会丢弃部分记录，获取丢弃了多少记录
            final int dropped = droppedRecordsUpdater.get(this);
            int duped = 0;

            // 由于每次在链表新增头部时，其pos=旧的pos + 1 , 因此最新的链表头部的pos就是链表的长度
            int present = oldHead.pos + 1;
            // Guess about 2 kilobytes per stack trace
            // 设置buf容量（大概为2KB栈信息 * 链表长度  ） ，并添加换行符
            StringBuilder buf = new StringBuilder(present * 2048).append(NEWLINE);
            buf.append("Recent access records: ").append(NEWLINE);

            int i = 1;
            Set<String> seen = new HashSet<String>(present);
            for (; oldHead != Record.BOTTOM; oldHead = oldHead.next) {
                // 获取调用栈信息
                String s = oldHead.toString();
                if (seen.add(s)) {
                    // 遍历到最初的记录与其他节点输出有所不同
                    if (oldHead.next == Record.BOTTOM) {
                        buf.append("Created at:").append(NEWLINE).append(s);
                    } else {
                        buf.append('#').append(i++).append(':').append(NEWLINE).append(s);
                    }
                } else {
                    // 出现重复的记录
                    duped++;
                }
            }
            // 当出现重复的记录时， 加上特殊的日志
            if (duped > 0) {
                buf.append(": ")
                        .append(duped)
                        .append(" leak records were discarded because they were duplicates")
                        .append(NEWLINE);
            }
            // 若出现记录数超过了TARGET_RECORDS(默认为4)， 则输出丢弃了多少记录等额外信息，可以通过设置
            // io.netty.leakDetection.targetRecords来修改记录的长度
            if (dropped > 0) {
                buf.append(": ")
                   .append(dropped)
                   .append(" leak records were discarded because the leak record count is targeted to ")
                   .append(TARGET_RECORDS)
                   .append(". Use system property ")
                   .append(PROP_TARGET_RECORDS)
                   .append(" to increase the limit.")
                   .append(NEWLINE);
            }

            buf.setLength(buf.length() - NEWLINE.length());
            return buf.toString();
        }
    }

    private static final AtomicReference<String[]> excludedMethods =
            new AtomicReference<String[]>(EmptyArrays.EMPTY_STRINGS);

    public static void addExclusions(Class clz, String ... methodNames) {
        Set<String> nameSet = new HashSet<String>(Arrays.asList(methodNames));
        // Use loop rather than lookup. This avoids knowing the parameters, and doesn't have to handle
        // NoSuchMethodException.
        for (Method method : clz.getDeclaredMethods()) {
            if (nameSet.remove(method.getName()) && nameSet.isEmpty()) {
                break;
            }
        }
        if (!nameSet.isEmpty()) {
            throw new IllegalArgumentException("Can't find '" + nameSet + "' in " + clz.getName());
        }
        String[] oldMethods;
        String[] newMethods;
        do {
            oldMethods = excludedMethods.get();
            newMethods = Arrays.copyOf(oldMethods, oldMethods.length + 2 * methodNames.length);
            for (int i = 0; i < methodNames.length; i++) {
                newMethods[oldMethods.length + i * 2] = clz.getName();
                newMethods[oldMethods.length + i * 2 + 1] = methodNames[i];
            }
        } while (!excludedMethods.compareAndSet(oldMethods, newMethods));
    }

    private static final class Record extends Throwable {
        private static final long serialVersionUID = 6065153674892850720L;

        private static final Record BOTTOM = new Record();

        private final String hintString;
        private final Record next;
        private final int pos;

        Record(Record next, Object hint) {
            // This needs to be generated even if toString() is never called as it may change later on.
            hintString = hint instanceof ResourceLeakHint ? ((ResourceLeakHint) hint).toHintString() : hint.toString();
            this.next = next;
            this.pos = next.pos + 1;
        }

        Record(Record next) {
           hintString = null;
           this.next = next;
           this.pos = next.pos + 1;
        }

        // Used to terminate the stack
        private Record() {
            hintString = null;
            next = null;
            pos = -1;
        }

        @Override
        // Record 的toString() 方法获取Record创建时的调用栈信息
        public String toString() {
            StringBuilder buf = new StringBuilder(2048);
            // 先添加提示信息
            if (hintString != null) {
                buf.append("\tHint: ").append(hintString).append(NEWLINE);
            }

            // Append the stack trace.
            // 再添加栈信息
            StackTraceElement[] array = getStackTrace();
            // Skip the first three elements.
            // 跳过前面的3个栈元素 ， 因为他们是record()方法的栈信息，显示没有意义
            out: for (int i = 3; i < array.length; i++) {
                StackTraceElement element = array[i];
                // Strip the noisy stack trace elements.
                // 跳过一些不必要的方法信息
                String[] exclusions = excludedMethods.get();
                for (int k = 0; k < exclusions.length; k += 2) {
                    if (exclusions[k].equals(element.getClassName())
                            && exclusions[k + 1].equals(element.getMethodName())) {
                        continue out;
                    }
                }
                // 格式化
                buf.append('\t');
                buf.append(element.toString());
                // 加上换行
                buf.append(NEWLINE);
            }
            return buf.toString();
        }
    }


    // 本章主要对Netty的NioEventLoop线程、Channel、ByteBuf缓冲 区、内存泄漏检测机制进行了详细的剖析。这些组件都是Netty的核 心，
    // 在学习其源码的同时，要思考其整体设计思想，如Channel和 ByteBuf的整体设计及其每层抽象类的意义;在这些组件中，Netty对 哪些部分做了性能优化，
    // 如运用JDK的Unsafe、乐观锁、对象池、 SelectedSelectionKeySet数据结构优化等。除了本章的核心组件， Netty还有Handler事件驱动模型、编码和解码、
    // 时间轮、复杂的内存 池管理等，在后续章节会进行详细的剖析。
    public static void main(String[] args) {
        // 创建字符串对象
        String str = new String("内存泄漏检测 ");
        // 创建一个引用队列
        ReferenceQueue referenceQueue = new ReferenceQueue<>();
        // 创建一个弱引用，弱引用引用str字符串
        WeakReference weakReference = new WeakReference(str, referenceQueue);
        // 切断str 引用和内存泄漏检测，字符串之间的引用
        str = null;
        // 取出弱引用所引用的对象,若是虚引用，则虚引用无法获取被引用的对象
        System.out.println(weakReference.get());
        System.gc();
        // 强制垃圾回收
        System.runFinalization();
        // 垃圾回收后，弱引用将被放到引用队列，取出引用队列中的引用并与weakReference进行比较，应输出true
        System.out.println(referenceQueue.poll() == weakReference);
    }



}
