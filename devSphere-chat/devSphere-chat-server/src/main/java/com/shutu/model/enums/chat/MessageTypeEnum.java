package com.shutu.model.enums.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 消息状态
 */
@AllArgsConstructor
@Getter
public enum MessageTypeEnum {

    TEXT(1, "正常文本"),
    RECALL(2, "撤回消息"),
    IMAGE(3, "图片"),
    VOICE(4, "语音"),
    VIDEO(5, "视频"),
    FILE(6, "文件");

    private final Integer type;
    private final String desc;

    private static final Map<Integer, MessageTypeEnum> CACHE;

    static {
        CACHE = Arrays.stream(MessageTypeEnum.values()).collect(Collectors.toMap(MessageTypeEnum::getType, Function.identity()));
    }

    public static MessageTypeEnum of(Integer type) {
        return CACHE.get(type);
    }
}
