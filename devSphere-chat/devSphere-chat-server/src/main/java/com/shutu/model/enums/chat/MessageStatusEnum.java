package com.shutu.model.enums.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息状态枚举
 * 对应 dev_sphere_message 表中的 status 字段
 */
@Getter
@AllArgsConstructor
public enum MessageStatusEnum {

    NORMAL(0, "正常"),
    DELETED(1, "删除");

    private final Integer status;
    private final String desc;
}