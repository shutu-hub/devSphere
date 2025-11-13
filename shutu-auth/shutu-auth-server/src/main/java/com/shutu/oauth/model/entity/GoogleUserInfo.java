package com.shutu.oauth.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.SneakyThrows;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleUserInfo implements OAuthUserInfo {

    // `sub` 是 OpenID Connect 标准中用于唯一标识用户的主体字段
    @JsonProperty("sub")
    private String id;
    
    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    @JsonProperty("picture")
    private String avatarUrl;

    private String rawUserInfo;
    
    @Override
    public String getUniqueId() {
        return this.id;
    }

    @Override
    public String getNickname() {
        return this.name;
    }

    @Override
    public String getAvatarUrl() {
        return this.avatarUrl;
    }

    @Override
    public String getPlatform() {
        return "google";
    }

    @SneakyThrows
    @Override
    public String getRawUserInfo() {
        if (this.rawUserInfo == null) {
            this.rawUserInfo = new ObjectMapper().writeValueAsString(this);
        }
        return this.rawUserInfo;
    }
}