package io.netty.actual.combat.e1;

import com.google.common.util.concurrent.*;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by 尼恩 at 疯狂创客圈
 */

public class GuavaFutureDemo {

    public static final int SLEEP_GAP = 500;


    public static String getCurThreadName() {
        return Thread.currentThread().getName();
    }

    static class HotWarterJob implements Callable<Boolean> //①
    {

        @Override
        public Boolean call() throws Exception //②
        {

            try {
                Logger.info("洗好水壶");
                Logger.info("灌上凉水");
                Logger.info("放在火上");

                //线程睡眠一段时间，代表烧水中
                Thread.sleep(SLEEP_GAP);
                Logger.info("水开了");

            } catch (InterruptedException e) {
                Logger.info(" 发生异常被中断.");
                return false;
            }
            Logger.info(" 烧水工作，运行结束.");

            return true;
        }
    }

    static class WashJob implements Callable<Boolean> {

        @Override
        public Boolean call() throws Exception {


            try {
                Logger.info("洗茶壶");
                Logger.info("洗茶杯");
                Logger.info("拿茶叶");
                //线程睡眠一段时间，代表清洗中
                Thread.sleep(SLEEP_GAP);
                Logger.info("洗完了");

            } catch (InterruptedException e) {
                Logger.info(" 清洗工作 发生异常被中断.");
                return false;
            }
            Logger.info(" 清洗工作  运行结束.");
            return true;
        }

    }

    //泡茶线程
    static class MainJob implements Runnable {

        boolean warterOk = false;
        boolean cupOk = false;
        int gap = SLEEP_GAP / 10;

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(gap);
                    Logger.info("读书中......");
                } catch (InterruptedException e) {
                    Logger.info(getCurThreadName() + "发生异常被中断.");
                }

                if (warterOk && cupOk) {
                    drinkTea(warterOk, cupOk);
                }
            }
        }


        public void drinkTea(Boolean wOk, Boolean cOK) {
            if (wOk && cOK) {
                Logger.info("泡茶喝，茶喝完");
                this.warterOk = false;
                this.gap = SLEEP_GAP * 100;
            } else if (!wOk) {
                Logger.info("烧水失败，没有茶喝了");
            } else if (!cOK) {
                Logger.info("杯子洗不了，没有茶喝了");
            }

        }
    }

    public static void main(String args[]) {

        //新起一个线程，作为泡茶主线程
        MainJob mainJob = new MainJob();
        Thread mainThread = new Thread(mainJob);
        mainThread.setName("主线程");
        mainThread.start();

        //烧水的业务逻辑
        Callable<Boolean> hotJob = new HotWarterJob();
        //清洗的业务逻辑
        Callable<Boolean> washJob = new WashJob();

        //创建java 线程池
        ExecutorService jPool =
                Executors.newFixedThreadPool(10);

        //包装java线程池，构造guava 线程池
        ListeningExecutorService gPool =
                MoreExecutors.listeningDecorator(jPool);

        //提交烧水的业务逻辑，取到异步任务
        ListenableFuture<Boolean> hotFuture = gPool.submit(hotJob);
        //绑定任务执行完成后的回调，到异步任务
        Futures.addCallback(hotFuture, new FutureCallback<Boolean>() {
            public void onSuccess(Boolean r) {
                if (r) {
                    mainJob.warterOk = true;
                }
            }

            public void onFailure(Throwable t) {
                Logger.info("烧水失败，没有茶喝了");
            }
        });
        //提交清洗的业务逻辑，取到异步任务

        ListenableFuture<Boolean> washFuture = gPool.submit(washJob);
        //绑定任务执行完成后的回调，到异步任务
        Futures.addCallback(washFuture, new FutureCallback<Boolean>() {
            public void onSuccess(Boolean r) {
                if (r) {
                    mainJob.cupOk = true;
                }
            }

            public void onFailure(Throwable t) {
                Logger.info("杯子洗不了，没有茶喝了");
            }
        });

        // Guava 的异步回调和FutureTask的异步回调，本质上的不同在于  ：
        // Guava 是非阻塞的异步回调 ， 调用线程是不阻塞的， 可以继续执行自己的业务逻辑 。
        // FutureTask是阻塞的异步回调， 调用线程是阻塞的， 在获取异步结果的过程中， 一直阻塞等待异步返回结果 。
        // 5.6 Netty异步回调模式
        // Netty 官方文档中指出Netty是网络操作都是异步的，在Netty源代码中，大量的使用异步回调处理模式，在Netty的业务开发层面，Netty
        // 应用 的Handler处理器中rogtlwadc，也都是异步执行的，所以，了解了Netty的异步回调， 无论是Netty 应用级的开发还是源代码的开发
        // 都是十分重要的。
        // Netty和Guava一样，实现了自己的异步回调体系，Netty 继承和扩展了JDK Future系统异步回调的API,定义了自身的Future系统接口和类。
        // 实现了异步任务的监控， 异步执行结果的获取 。
        // 总体来说， Netty 对Java 异步任务的扩展如下
        // 1. 继承Java 的Future接口，得到了一个新的属于Netty 自己的Future异步任务接口，该接口对原有的接口进行了增强，使得Netty 异步任务能够以非阻塞的方式处理回调的结果
        // 注意，Netty 没有修改Future的名称，只是调整了所在包名。 Netty的Future类的包名和Java Future接口的包名不同。
        // 2. 引入了一个新的接口，GenericFutureLister ， 用于表示异步执行完成的监听器， 这个接口和Guava的FutureCallback回调接口不同，Netty
        // 的GenericFutureListener 监听器接口加入了Netty异步任务Future中，实现了对异步任务执行状态的事件监听 。
        // 总体来说，在异步 非阻塞回调的设计思路上，Netty 和Guava的思路是一致的，对应关系为：
        // Netty  的Future 接口，可以对应于Guava 的Listenabale的Future接口。
        // Netty 的GenericFutureListener接口，可以对应于Guava的FutureCallback接口。
        //
    }


}