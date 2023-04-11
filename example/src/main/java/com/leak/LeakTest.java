package com.leak;


public class LeakTest {

    public static void main(String[] args) {
        MyByteBuf buf = new MyByteBuf();

        MyResourceLeakDetector leakDetector = new MyResourceLeakDetector();

        MyResourceLeakTracker<MyByteBuf> leak = leakDetector.track(buf);

        MyAdvancedLeakAwareByteBuf wrapperBuf= new MyAdvancedLeakAwareByteBuf(buf, leak);

        wrapperBuf.getInt();

        wrapperBuf.setInt(1);


      leak.toString();



    }
}
