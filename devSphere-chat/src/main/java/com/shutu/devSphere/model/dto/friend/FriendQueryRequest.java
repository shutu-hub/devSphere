package com.shutu.devSphere.model.dto.friend;

import lombok.Data;

/**
 * 用户或群聊查询请求
 */
@Data
public class FriendQueryRequest {
    /**
     * 房间 ID（房间id后缀有s）或用户id
     */
    private String id;
}
