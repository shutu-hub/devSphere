package com.shutu.oauth.service;

import com.shutu.oauth.model.entity.OAuthUserInfo;
import com.shutu.model.dto.UserTokenDTO;

/**
 * 统一的OAuth用户处理服务
 * 负责根据第三方用户信息，在本地系统中进行注册或登录，并生成系统Token
 */
public interface OAuthUserService {

    /**
     * 根据第三方平台的用户信息进行登录或注册
     * @param userInfo 标准化的用户信息
     * @return 包含系统内部token的DTO
     * @throws InterruptedException 如果获取分布式锁被中断
     */
    UserTokenDTO loginOrRegister(OAuthUserInfo userInfo) throws InterruptedException;
}