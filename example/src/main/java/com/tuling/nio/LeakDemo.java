package com.tuling.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

// VM Args:-Dio.netty.leakDetection.level=PARANOID 100%采样检测
public class LeakDemo {
	public static void main(String[] args) throws InterruptedException {
		ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer(1024);
		buf = null;
		System.gc();
		Thread.sleep(1000);
		// 再申请一次，此时会检测到泄漏并报告
		PooledByteBufAllocator.DEFAULT.buffer(1024);
	}

	@Override
	protected void finalize() throws Throwable {
		System.out.println("finalize...");
	}
}