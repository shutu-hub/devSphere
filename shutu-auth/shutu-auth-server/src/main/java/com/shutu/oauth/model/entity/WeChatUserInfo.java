package com.shutu.oauth.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;

/**
 * 适配器类，将微信官方SDK的WxOAuth2UserInfo对象适配到我们自己的OAuthUserInfo接口
 */
@RequiredArgsConstructor
public class WeChatUserInfo implements OAuthUserInfo {

    private final WxOAuth2UserInfo wxUserInfo;
    private String rawUserInfo;

    @Override
    public String getUniqueId() {
        // 优先使用 UnionId 作为跨应用唯一标识
        return wxUserInfo.getUnionId();
    }

    @Override
    public String getNickname() {
        return wxUserInfo.getNickname();
    }

    @Override
    public String getAvatarUrl() {
        return wxUserInfo.getHeadImgUrl();
    }

    @Override
    public String getPlatform() {
        return "wechat";
    }

    @SneakyThrows
    @Override
    public String getRawUserInfo() {
        if (this.rawUserInfo == null) {
            // 使用Jackson进行序列化
            this.rawUserInfo = new ObjectMapper().writeValueAsString(wxUserInfo);
        }
        return this.rawUserInfo;
    }
}