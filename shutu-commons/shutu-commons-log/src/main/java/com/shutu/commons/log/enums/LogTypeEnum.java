package com.shutu.commons.log.enums;

/**
 * 日志类型枚举
 */
public enum LogTypeEnum {
    /**
     * 登录日志
     */
    LOGIN(0),
    /**
     * 操作日志
     */
    OPERATION(1),
    /**
     * 异常日志
     */
    ERROR(2);

    private final int value;

    LogTypeEnum(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }
}