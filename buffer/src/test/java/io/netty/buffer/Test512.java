package io.netty.buffer;

public class Test512 {

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder();
        for(int i = 16 ;i <512 ;i = i + 16 ){
            sb.append(i).append("B").append(",");
        }
        System.out.println(sb.toString());
    }
}
