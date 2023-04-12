package com.shengshiyuan.bytebuf;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class AtomicIntegerFieldUpdaterTest {

    public static void main(String[] args) throws Exception {
        //        Person person = new Person();
        //
        //        for (int i = 0; i < 10; ++i) {
        //            Thread thread = new Thread(() -> {
        //
        //                try {
        //                    Thread.sleep(20);
        //                } catch (InterruptedException e) {
        //                    e.printStackTrace();
        //                }
        //
        //                System.out.println(person.age++);
        //            });
        //
        //            thread.start();
        //        }


        User user = new User();
        // 如果更新器是AtomicIntegerFieldUpdater
        // 1. 更新器更新的必须是int类型的变量，不能是其包装类型。
        // 2. 更新器更新的必须是volatile变量 , volatile防止指令重排序 ，确保线程之间共享变量时的立即可见性
        // 3. 变量不能是static，必须要是实例变量，因为Unsafe.objectFieldOffset()方法不支持静态变量(CAS操作本质上是通过对象实例的偏移量来直接进行赋值的)
        // 4. 更新器只能修改它可见范围内的变量，因为更新器是通过反射来得到这个变量的,如果这个变量不可见，就报错
        // 如果要更新的变量是包装类型的变量，那么可以使用AtomicReferenceFieldUpdater来实现。
        AtomicIntegerFieldUpdater<User> atomicIntegerFieldUpdater = AtomicIntegerFieldUpdater.
                newUpdater(User.class, "age");

        for (int i = 0; i < 10; ++i) {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                atomicIntegerFieldUpdater.incrementAndGet(user);
            });
            thread.start();
        }
        Thread.sleep(2000);

        System.out.println(atomicIntegerFieldUpdater.get(user));

    }
}


class User {
    // Exception in thread "main" java.lang.IllegalArgumentException: Must be integer type
    //	at java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl.<init>(AtomicIntegerFieldUpdater.java:409)
    //	at java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdater.java:88)
    // volatile Integer age = 0;
    volatile int age = 0;

}
