package com.shutu.oauth.model.entity;

/**
 * 标准化的第三方用户信息接口
 */
public interface OAuthUserInfo {

    /**
     * 获取第三方平台的唯一用户ID (例如：unionId, openId, gitee's id)
     * @return 平台唯一ID
     */
    String getUniqueId();

    /**
     * 获取用户昵称
     * @return 昵称
     */
    String getNickname();

    /**
     * 获取用户头像URL
     * @return 头像URL
     */
    String getAvatarUrl();

    /**
     * 获取用户来源平台
     * @return 平台名称 ("gitee", "wechat", "qq")
     */
    String getPlatform();

    /**
     * 获取原始返回的用户信息JSON字符串，用于扩展或调试
     * @return 原始JSON
     */
    String getRawUserInfo();
}