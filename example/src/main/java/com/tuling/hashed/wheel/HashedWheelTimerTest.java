package com.tuling.hashed.wheel;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class HashedWheelTimerTest {


    public static void main(String[] args) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        HashedWheelTimer timer = new HashedWheelTimer(1, TimeUnit.SECONDS, 16);
        final long start = System.currentTimeMillis();
        Timeout timeout = timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                System.out.println("task execute,current timestamp=" + (System.currentTimeMillis() - start));
                countDownLatch.countDown();
            }
        }, 2000, TimeUnit.MILLISECONDS);

        //Thread.sleep(1000);
        //timeout.cancel();

        countDownLatch.await();
        System.out.println("============================" + (System.currentTimeMillis() - start));
        timer.stop();
    }
}
