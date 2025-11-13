package com.shutu.oauth.service;

import com.alipay.api.AlipayApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.shutu.oauth.model.entity.OAuthUserInfo;

/**
 * OAuth认证服务策略接口
 * 所有第三方登录平台都需要实现此接口
 */
public interface OAuthService {

    /**
     * 构建授权URL
     * @param state CSRF防护随机字符串
     * @return 授权页面的完整URL
     */
    String buildAuthorizeUrl(String state);

    /**
     * 使用授权码(code)获取第三方平台的用户信息
     * 此方法内部会完成"用code换token"和"用token换userInfo"两步
     * @param code 授权码
     * @return 标准化的用户信息对象
     */
    OAuthUserInfo getUserInfo(String code) throws AlipayApiException, JsonProcessingException;

    /**
     * 获取当前服务对应的平台名称
     * @return 平台名称 (gitee, wechat, qq)
     */
    String getPlatform();
}