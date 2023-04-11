package com.leak;

final class MyAdvancedLeakAwareByteBuf extends MyByteBuf {

    private MyByteBuf buf;

    private MyResourceLeakTracker leak;

    MyAdvancedLeakAwareByteBuf(MyByteBuf buf, MyResourceLeakTracker<MyByteBuf> leak) {
        this.buf = buf;
        this.leak = leak;
    }


    public int getInt() {
        recordLeakNonRefCountingOperation(leak);
        return buf.getInt();
    }

    public int setInt(int index) {
        recordLeakNonRefCountingOperation(leak);
        return buf.getInt();
    }


    static void recordLeakNonRefCountingOperation(MyResourceLeakTracker<MyByteBuf> leak) {
        leak.record();
    }


}


