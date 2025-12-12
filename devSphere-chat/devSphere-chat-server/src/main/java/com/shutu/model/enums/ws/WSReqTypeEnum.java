package com.shutu.model.enums.ws;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum WSReqTypeEnum {
    LOGIN(1, "登录"),
    CHAT(2, "聊天消息"),
    AUTHORIZE(3, "鉴权"),
    HEARTBEAT(4, "心跳"),
    ERROR(5, "错误通知"),
    ACK(6, "消息确认"),
    RTC_SIGNAL(10, "RTC信令"),
    AI_INTERVIEW(11, "AI面试");

    private final Integer type;
    private final String desc;

    private static final Map<Integer, WSReqTypeEnum> cache;

    static {
        cache = Arrays.stream(WSReqTypeEnum.values())
                .collect(Collectors.toMap(WSReqTypeEnum::getType, Function.identity()));
    }

    public static WSReqTypeEnum of(Integer type) {
        return cache.get(type);
    }
}