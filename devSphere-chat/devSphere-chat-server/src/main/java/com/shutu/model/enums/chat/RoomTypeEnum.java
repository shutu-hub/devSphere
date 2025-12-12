package com.shutu.model.enums.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 房间(会话)类型枚举
 */
@AllArgsConstructor
@Getter
public enum RoomTypeEnum {
    GROUP(1, "群聊"),
    PRIVATE(2, "私聊");

    private final Integer type;
    private final String desc;

    private static final Map<Integer, RoomTypeEnum> CACHE;

    static {
        CACHE = Arrays.stream(RoomTypeEnum.values()).collect(Collectors.toMap(RoomTypeEnum::getType, Function.identity()));
    }

    public static RoomTypeEnum of(Integer type) {
        return CACHE.get(type);
    }
}