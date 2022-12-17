package io.netty.protocol;


import io.netty.actual.combat.e1.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * create by 尼恩 @ 疯狂创客圈
 **/
public class ProtobufDemo {


    public static void main(String[] args) throws Exception {
        ProtobufDemo demo = new ProtobufDemo();
        demo.serAndDesr1();
        demo.serAndDesr2();
        demo.serAndDesr3();
    }

    // Protobuf 为每个message消息结构生成的Java 类中，包含了一个POJO 类， 一个Builder类，构造POJO消息，首先需要使用POJO类的newBuilder()
    // 静态方法获得一个Builder构造者， 每一个POJO 字段的值，需要通过Builder构造者的setter方法去设置，注意，消息POJO 对象并没有setter方法
    // 字段的值设置完成后，使用构造者的build()方法构造出POJO 消息对象 。

    public static MsgProtos.Msg buildMsg() {
        MsgProtos.Msg.Builder personBuilder = MsgProtos.Msg.newBuilder();
        personBuilder.setId(1000);
        personBuilder.setContent("疯狂创客圈:高性能学习社群");
        MsgProtos.Msg message = personBuilder.build();
        return message;
    }

    //第1种方式:序列化 serialization & 反序列化 Deserialization
    // 这种方式通过调用POJO 对象的toByteArray()方法将POJO 对象序列化成字节数组，通过调用parseFrom(byte[] data)方法，Protobuf 也可以从字节数组中
    // 重新反序列化得到POJO 新的实例。
    // 这种方式类似于普通的Java对象的序列化，适用于很多的将Protobuf的POJO 序列化到内存或者外层的应用场景中。
    public void serAndDesr1() throws IOException {
        MsgProtos.Msg message = buildMsg();
        //将Protobuf对象，序列化成二进制字节数组
        byte[] data = message.toByteArray();
        //可以用于网络传输,保存到内存或外存
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(data);
        data = outputStream.toByteArray();
        //二进制字节数组,反序列化成Protobuf 对象
        MsgProtos.Msg inMsg = MsgProtos.Msg.parseFrom(data);
        Logger.info("id:=" + inMsg.getId());
        Logger.info("content:=" + inMsg.getContent());
    }

    // 第2种方式:序列化 serialization & 反序列化 Deserialization
    // 这种方式通过调用POJO 对象的writeTo(OutputStream ) 方法将POJO对象的二进制字节写出到输出流， 通过调用parseFrom(InputStream)
    // 方法，Protobuf从输出流中读取二进制码流重新反序列化，得到 POJO 新的实例。
    // 在阻塞式的二进制码流传输应用场景中，这种序列化和反序列化的方式是没有问题的， 例如可以将二进制流写入阻塞式的Java OIO 套接字或输出到文件
    // 但是，这种方式在异步操作的NIO 的应用场景中，存在着粘包/半包的问题
    public void serAndDesr2() throws IOException {
        MsgProtos.Msg message = buildMsg();
        //序列化到二进制流
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        //从二进流,反序列化成Protobuf 对象
        MsgProtos.Msg inMsg = MsgProtos.Msg.parseFrom(inputStream);
        Logger.info("id:=" + inMsg.getId());
        Logger.info("content:=" + inMsg.getContent());
    }


    //第3种方式:序列化 serialization & 反序列化 Deserialization
    //带字节长度：[字节长度][字节数据],解决粘包问题
    // 这种方式通过调用POJO 对象的writeDelimitedTo （Outputstream）方法在序列化的字节码之前添加了字节码数组的长度，这一点类似于前面介绍的
    // Head-Content协议，只不过Protobuf做了优化，长度的类型不是固定长度的int 类型，而是可变长度varint32
    // 反序列化时， 调用parseDelimitedFrom(InputStream) 方法，Protobuf从输入流中先取varint32类型的长度值，然后根据长度值读取此消息的
    // 二进制字节 ， 再反序列化得到 POJO新的实例。
    // 这种方式可以用于异步操作的NIO应用场景，解决粘包/半包的问题。
    public void serAndDesr3() throws IOException {
        MsgProtos.Msg message = buildMsg();
        //序列化到二进制流
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeDelimitedTo(outputStream);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        //从二进流,反序列化成Protobuf 对象
        MsgProtos.Msg inMsg = MsgProtos.Msg.parseDelimitedFrom(inputStream);
        Logger.info("id:=" + inMsg.getId());
        Logger.info("content:=" + inMsg.getContent());


    }


}
