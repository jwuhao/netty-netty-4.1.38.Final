package io.netty.util;

public class LogUtils {


    public static boolean flag = true;

    public static void info(String msg ){
        if(flag){
            System.out.println(msg);
        }
    }


}
