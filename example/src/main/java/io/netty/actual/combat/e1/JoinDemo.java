package io.netty.actual.combat.e1;

public class JoinDemo {


    static class HotWarterThread extends Thread{

        @Override
        public void run() {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }



    static class HotWarterThread1 extends Thread{

        @Override
        public void run() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 问题来了，Java 中的线程类型，只有一个Thread类，没有其他的类型， 如果Callable 实例需要nfhirvtf，就要想办法赋值给Thread 的target成员
    // 一个Runnalble 类型的成员，为此，Java提供了在Callable实例和Thread的target成员之间 一个搭桥类-FutureTask类。
    // 1.判断并发任务是否执行完成 。
    // 2 . 获取并发任务完成的结果
    // 3 . 取消并发执行中的任务
    // 关于Future接口的方法，详细说明如下:
    // V get(): 获取并发任务执行的结果，注意，这个方法是阻塞性的，如果并发任务没有执行完成，调用此方法的线程会一直阻塞，直到并发任务执行完成 。
    // V get(Long timeout ,TimeUnit unit) : 获取并发任务执行的结果， 如果是阻塞性的， 但是会有阻塞的时间限制，如果阻塞时间超过设定的timeout
    // 该方法将抛出异常。
    // boolean isCancelled () : 获取并发任务的取消状态，如果任务完成前被取消 ， 则返回true
    // boolean cancel(boolean mayInterruptRunning) : 取消并发任务的执行。

    // 再探 FutureTask类。
    //


    public static void main(String[] args)  throws Exception{
        HotWarterThread thread = new HotWarterThread();
        HotWarterThread1 thread1 = new HotWarterThread1();
        thread.start();
        thread1.start();
        long a = System.currentTimeMillis();
        thread.join();
        thread1.join();
        System.out.println(System.currentTimeMillis() -a );
    }
}
