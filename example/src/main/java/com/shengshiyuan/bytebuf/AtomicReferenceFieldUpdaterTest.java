package com.shengshiyuan.bytebuf;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class AtomicReferenceFieldUpdaterTest {

    public static void main(String[] args) throws Exception {

        Person person = new Person();
        // 如果更新器是AtomicIntegerFieldUpdater
        // 1. 更新器更新的必须是int类型的变量，不能是其包装类型。
        // 2. 更新器更新的必须是volatile变量 , volatile防止指令重排序 ，确保线程之间共享变量时的立即可见性
        // 3. 变量不能是static，必须要是实例变量，因为Unsafe.objectFieldOffset()方法不支持静态变量(CAS操作本质上是通过对象实例的偏移量来直接进行赋值的)
        // 4. 更新器只能修改它可见范围内的变量，因为更新器是通过反射来得到这个变量的,如果这个变量不可见，就报错
        // 如果要更新的变量是包装类型的变量，那么可以使用AtomicReferenceFieldUpdater来实现。
        AtomicReferenceFieldUpdater<Person, Integer> atomicIntegerFieldUpdater = AtomicReferenceFieldUpdater.
                newUpdater(Person.class, Integer.class, "age");

        for (int i = 0; i < 10; ++i) {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                while (true) {
                    Integer oldAge = atomicIntegerFieldUpdater.get(person);
                    Integer newAge = oldAge + 1;
                    if (atomicIntegerFieldUpdater.compareAndSet(person, oldAge, newAge)) {
                        break;
                    } else {
                        System.out.println(Thread.currentThread().getName() + "存在锁竞争");
                    }
                }
            });
            thread.start();
        }
        Thread.sleep(2000);

        System.out.println(atomicIntegerFieldUpdater.get(person));
    }
}


class Person {
    volatile Integer age = 0;
}
