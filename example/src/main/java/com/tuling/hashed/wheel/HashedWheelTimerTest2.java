package com.tuling.hashed.wheel;

import java.util.concurrent.CountDownLatch;


public class HashedWheelTimerTest2 {


    public static void main(String[] args) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final long start = System.currentTimeMillis();

        for(int i = 0 ;i <  3 ;i ++){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        countDownLatch.await();
                        System.out.println("===========" + (System.currentTimeMillis() - start));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("----------start------------------");
                    Thread.sleep(3000);
                    countDownLatch.countDown();
                    System.out.println("--------------end -----------------");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
}
