package com.shutu.devSphere.model.enums.chat;

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
public enum FriendTargetTypeEnum {
    JOIN(1, "已加入"),
    UN_JOIN(2, "未加入"),
    ;

    private final Integer type;
    private final String desc;

    private static final Map<Integer, FriendTargetTypeEnum> CACHE;

    static {
        CACHE = Arrays.stream(FriendTargetTypeEnum.values()).collect(Collectors.toMap(FriendTargetTypeEnum::getType, Function.identity()));
    }

    public static FriendTargetTypeEnum of(Integer type) {
        return CACHE.get(type);
    }
}
