package io.netty.util.concurrent;

import sun.net.idn.Punycode;

public class Testaa {


    public static void main(String[] args) {
        System.out.println(test());


    }


    public static int test(){

        int i = 0 ;
        try {
            i = 1;
            int j = 0;
            int c=  i / j ;
            return i ;
        }catch (Exception e ){
           e.printStackTrace();
            return 3;
        }finally {

            i = 2;
        }
    }


//   0: iconst_0
//   1: istore_0
//   2: iconst_1
//   3: istore_0
//   4: iconst_0
//   5: istore_1
//   6: iload_0
//   7: iload_1
//   8: idiv
//   9: istore_2
//  10: iload_0
//  11: istore_3
//  12: iconst_2
//  13: istore_0
//  14: iload_3
//  15: ireturn
//  16: astore_1
//  17: aload_1
//  18: invokevirtual #6                  // Method java/lang/Exception.printStackTrace:()V
//        21: iconst_3
//  22: istore_2
//  23: iconst_2
//  24: istore_0
//  25: iload_2
//  26: ireturn
//  27: astore        4
//        29: iconst_2
//  30: istore_0
//  31: aload         4
//        33: athrow


}

