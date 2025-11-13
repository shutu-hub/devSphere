package com.shutu.oauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "oauth2")
public class OAuthProperties {

    /**
     * key: 平台名称, gitee, wechat, qq, alipay
     * value: 对应平台的配置
     */
    private Map<String, ClientProperties> clients;

    @Data
    public static class ClientProperties {

        // --- 通用OAuth 2.0 字段 (适用于Gitee, GitHub, Google等) ---
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String scope;
        private String authorizeUrl;
        private String tokenUrl;
        private String userInfoUrl;

        // --- 特殊平台预留字段 ---
        private String openIdUrl; // 例如: QQ获取OpenID的URL

        // 为支付宝新增的专属字段
        /**
         * 支付宝应用私钥 (非常重要，注意保密)
         */
        private String privateKey;

        /**
         * 支付宝公钥 (用于验签)
         */
        private String alipayPublicKey;

        /**
         * 支付宝统一网关地址
         */
        private String gatewayUrl;

        /**
         * 签名算法类型，固定为 RSA2
         */
        private String signType;

        /**
         * 字符集编码，固定为 utf-8
         */
        private String charset;
    }
}