package com.leak;


public interface MyResourceLeakTracker<T> {




    void record();


    void record(Object hint);


    boolean close(T trackedObject);


}
