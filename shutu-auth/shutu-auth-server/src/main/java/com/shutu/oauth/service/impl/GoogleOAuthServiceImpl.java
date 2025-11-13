package com.shutu.oauth.service.impl;

import com.shutu.oauth.config.OAuthProperties;
import com.shutu.oauth.model.entity.GoogleUserInfo;
import com.shutu.oauth.model.entity.OAuthUserInfo;
import com.shutu.oauth.service.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthServiceImpl implements OAuthService {

    private final WebClient webClient;
    private final OAuthProperties oAuthProperties;

    @Override
    public String buildAuthorizeUrl(String state) {
        OAuthProperties.ClientProperties client = getClientProperties();
        return UriComponentsBuilder.fromHttpUrl(client.getAuthorizeUrl())
                .queryParam("client_id", client.getClientId())
                .queryParam("redirect_uri", client.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", client.getScope())
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    @Override
    public OAuthUserInfo getUserInfo(String code) {
        String accessToken = getAccessToken(code);
        GoogleUserInfo userInfo = webClient.get()
                .uri(getClientProperties().getUserInfoUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GoogleUserInfo.class)
                .doOnError(e -> log.error("获取Google用户信息失败", e))
                .block();
        
        Assert.notNull(userInfo, "获取Google用户信息失败");
        return userInfo;
    }

    private String getAccessToken(String code) {
        OAuthProperties.ClientProperties client = getClientProperties();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("client_id", client.getClientId());
        formData.add("client_secret", client.getClientSecret());
        formData.add("redirect_uri", client.getRedirectUri());

        Map<String, Object> response = webClient.post()
                .uri(client.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map.class)
                .doOnError(e -> log.error("获取Google AccessToken失败", e))
                .block();

        Assert.notNull(response, "获取Google AccessToken失败");
        return (String) response.get("access_token");
    }

    @Override
    public String getPlatform() {
        return "google";
    }

    private OAuthProperties.ClientProperties getClientProperties() {
        return oAuthProperties.getClients().get(getPlatform());
    }
}