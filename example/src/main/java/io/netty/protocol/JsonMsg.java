package io.netty.protocol;


import io.netty.utils.JsonUtil;

/**
 * create by 尼恩 @ 疯狂创客圈
 **/
public class JsonMsg {
    //id Field(域)
    private int id;
    //content Field(域)
    private String content;

    //在通用方法中，使用阿里FastJson转成Java对象
    public static JsonMsg parseFromJson(String json) {
        return JsonUtil.jsonToPojo(json, JsonMsg.class);
    }

    //在通用方法中，使用谷歌Gson转成字符串
    public String convertToJson() {
        return JsonUtil.pojoToJson(this);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
