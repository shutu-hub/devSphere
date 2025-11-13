package com.shutu.oauth.service.impl;

import com.shutu.oauth.config.OAuthProperties;
import com.shutu.oauth.model.entity.OAuthUserInfo;
import com.shutu.oauth.model.entity.WeChatUserInfo;
import com.shutu.oauth.service.OAuthService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatOAuthServiceImpl implements OAuthService {

    private final OAuthProperties oAuthProperties;
    private WxMpService wxMpService;

    @PostConstruct
    public void init() {
        OAuthProperties.ClientProperties clientProperties = getClientProperties();
        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        config.setAppId(clientProperties.getClientId());
        config.setSecret(clientProperties.getClientSecret());
        this.wxMpService = new WxMpServiceImpl();
        this.wxMpService.setWxMpConfigStorage(config);
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        OAuthProperties.ClientProperties clientProperties = getClientProperties();
        return wxMpService.getOAuth2Service().buildAuthorizationUrl(
                clientProperties.getRedirectUri(),
                clientProperties.getScope(),
                state
        );
    }

    @Override
    @SneakyThrows
    public OAuthUserInfo getUserInfo(String code) {
        WxOAuth2AccessToken accessToken = wxMpService.getOAuth2Service().getAccessToken(code);
        WxOAuth2UserInfo wxUserInfo = wxMpService.getOAuth2Service().getUserInfo(accessToken, "zh_CN");

        Assert.notNull(wxUserInfo, "获取微信用户信息失败");
        Assert.hasText(wxUserInfo.getUnionId(), "微信UnionID为空，请检查公众号/应用是否绑定到开放平台");

        return new WeChatUserInfo(wxUserInfo);
    }

    @Override
    public String getPlatform() {
        return "wechat";
    }

    private OAuthProperties.ClientProperties getClientProperties() {
        return oAuthProperties.getClients().get(getPlatform());
    }
}