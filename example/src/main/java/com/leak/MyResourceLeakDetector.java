package com.leak;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class MyResourceLeakDetector<T> {

    public static final String EMPTY_STRING = "";
    public static final String NEWLINE = "\n";

    // 活跃的资源集合
    private final Set<MyDefaultResourceLeak<?>> allLeaks =
            Collections.newSetFromMap(new ConcurrentHashMap<MyDefaultResourceLeak<?>, Boolean>());

    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<Object>();

    public final MyDefaultResourceLeak<T> track(T obj) {
        return track0(obj);
    }

    private MyDefaultResourceLeak track0(T obj) {
        // 偏执级别都采集
        reportLeak();
        return new MyDefaultResourceLeak(obj, refQueue, allLeaks);
    }

    private void reportLeak() {
        // Detect and report previous leaks.
        // 循环获取引用队列中的弱引用
        for (; ; ) {
            @SuppressWarnings("unchecked")
            MyDefaultResourceLeak ref = (MyDefaultResourceLeak) refQueue.poll();
            if (ref == null) {
                // 没有泄漏对象，则退出循环
                break;
            }
            // 获取buf的调用栈信息
            String records = ref.toString();
            System.out.println("=============recodes = " + records);
        }
    }

    private static final class MyDefaultResourceLeak<T> extends WeakReference<Object> implements MyResourceLeakTracker {

        @SuppressWarnings("unused")
        private volatile MyRecord head;
        @SuppressWarnings("unused")
        private volatile int droppedRecords;
        // 活跃的资源集合
        private final Set<MyDefaultResourceLeak<?>> allLeaks;
        // 追踪对象的一致性哈希码，确保关闭对象和追踪对象一致


        @SuppressWarnings("unchecked") // generics and updaters do not mix.
        private static final AtomicReferenceFieldUpdater<MyDefaultResourceLeak<?>, MyRecord> headUpdater =
                (AtomicReferenceFieldUpdater)
                        AtomicReferenceFieldUpdater.newUpdater(MyDefaultResourceLeak.class, MyRecord.class, "head");


        public MyDefaultResourceLeak(
                Object referent,
                ReferenceQueue<Object> refQueue,
                Set<MyDefaultResourceLeak<?>> allLeaks) {
            super(referent, refQueue);
            assert referent != null;

            allLeaks.add(this);
            // Create a new Record so we always have the creation stacktrace included.
            // 记录追踪的堆栈信息，TraceRecord.BOTTOM代表链尾
            MyRecord myRecord = new MyRecord(MyRecord.BOTTOM);
            headUpdater.set(this,myRecord );
            this.allLeaks = allLeaks;
        }

        @Override
        public void record() {
            record0(null);
        }

        private void record0(Object hint) {
            MyRecord oldHead;
            MyRecord prevHead;
            MyRecord newHead;
            do {
                // 判断记录链头是否为空，为空表示已经关闭，把之前的链头作为第二个元素赋值给新链表
                if ((prevHead = oldHead = headUpdater.get(this)) == null) {
                    // already closed.
                    return;
                }
                // 创建一个新的Record ，并将其添加到链表上， 作为链表的新的头部
                newHead = new MyRecord(prevHead);
            } while (!headUpdater.compareAndSet(this, oldHead, newHead));
        }

        @Override
        public void record(Object hint) {

        }

        @Override
        public boolean close(Object trackedObject) {
            return false;
        }

        @Override
        // 弱引用重写了toString()方法， 需要注意，若采用IDE工具debug 调试代码则在处理对象时，IDE 会自动调用toString()方法
        public String toString() {
           // 获取记录列表的头部
            MyRecord oldHead = headUpdater.getAndSet(this, null);
            // 若无记录，则返回空字符串
            if (oldHead == null) {
                // Already closed
                return EMPTY_STRING;
            }

            for (; oldHead != MyRecord.BOTTOM; oldHead = oldHead.next) {
                // 获取调用栈信息
                String s = oldHead.toString();
                System.out.println(s);
            }

            return "";
        }
    }

    private static final class MyRecord extends Throwable {
        private static final long serialVersionUID = 6065153674892850720L;

        public static final MyRecord BOTTOM = new MyRecord();
        // 下一个节点
        public final MyRecord next;
        public final int pos;

        /**
         * @param next 下一个节点
         * @param hint 额外的提示信息
         */
        MyRecord(MyRecord next, Object hint) {
            // This needs to be generated even if toString() is never called as it may change later on.
            //hintString = hint instanceof ResourceLeakHint ? ((ResourceLeakHint) hint).toHintString() : hint.toString();
            this.next = next;
            this.pos = next.pos + 1;
        }

        MyRecord(MyRecord next) {
            this.next = next;
            this.pos = next.pos + 1;
        }

        // Used to terminate the stack
        private MyRecord() {
            next = null;
            pos = -1;
        }


        @Override
        // Record 的toString() 方法获取Record创建时的调用栈信息
        public String toString() {
            StringBuilder buf = new StringBuilder(2048);
            // Append the stack trace.
            // 再添加栈信息
            StackTraceElement[] array = getStackTrace();
            // Skip the first three elements.
            // 跳过前面的3个栈元素 ， 因为他们是record()方法的栈信息，显示没有意义
            out:
            for (int i = 3; i < array.length; i++) {
                StackTraceElement element = array[i];
                // 格式化
                buf.append('\t');
                buf.append(element.toString());
                // 加上换行
                buf.append(NEWLINE);
            }
            return buf.toString();
        }
    }


}
