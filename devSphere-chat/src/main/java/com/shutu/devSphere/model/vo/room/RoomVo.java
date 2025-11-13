package com.shutu.devSphere.model.vo.room;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * VO室
 */
@Data
public class RoomVo {
    /**
     * 房间id
     */
    private Long id;

    /**
     * 房间类型 1群聊 2私聊
     */
    private Integer type;

    /**
     * 群最后消息的更新时间
     */
    private Date activeTime;

    /**
     * 会话中的最后一条消息
     */
    private String content;

    /**
     * 昵称
     */
    private String roomName;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 用户 ID (私聊时为对方ID，群聊时为群主ID)
     */
    private Long userId;

    /**
     * 未读
     */
    private int unreadNum = 0;

    /**
     * 群聊成员总数
     */
    @Schema(description = "群聊成员总数")
    private Integer memberCount;
}