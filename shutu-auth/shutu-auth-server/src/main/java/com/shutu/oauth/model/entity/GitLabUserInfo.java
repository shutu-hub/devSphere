package com.shutu.oauth.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.SneakyThrows;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabUserInfo implements OAuthUserInfo {

    @JsonProperty("id")
    private String id;

    @JsonProperty("username")
    private String username;

    @JsonProperty("name")
    private String name;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("email")
    private String email;

    private String rawUserInfo;
    
    @Override
    public String getUniqueId() {
        return String.valueOf(this.id);
    }

    @Override
    public String getNickname() {
        return this.name != null ? this.name : this.username;
    }

    @Override
    public String getAvatarUrl() {
        return this.avatarUrl;
    }

    @Override
    public String getPlatform() {
        return "gitlab";
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