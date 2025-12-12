package com.shutu.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新个人信息请求
 */
@Data
public class UserUpdateMyRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    private static final long serialVersionUID = 1L;
}