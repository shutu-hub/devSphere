package com.shutu.model.dto.user;

import lombok.Data;

/**
 * Data Transfer Object representing user information fetched via
 * UserFeignClient.
 */
@Data
public class UserDto {
    private Long userId;
    private String username;
    private String avatarUrl;
    private String displayName;
}
