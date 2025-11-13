package com.shutu.commons.log.enums;

/**
 * 登录操作枚举
 */
public enum LoginOperationEnum {
    /**
     * 登录成功
     */
    SUCCESS(0),
    /**
     * 登录失败
     */
    FAIL(1),
    /**
     * 用户退出
     */
    LOGOUT(2);


    private final int value;

    LoginOperationEnum(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }
}