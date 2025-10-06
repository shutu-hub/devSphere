package com.shutu.devSphere.model.vo.friend;

import lombok.Data;

/**
 * 好友内容
 */
@Data
public class FriendVo {

    /**
     * 房间id
     */
    private Long roomId;

    /**
     *用户id
     */
    private Long uid;

    /**
     * 头像
     */
    private String avatar;
    /**
     * 名字
     */
    private String name;
}
