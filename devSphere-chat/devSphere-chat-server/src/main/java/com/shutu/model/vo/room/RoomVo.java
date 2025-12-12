package com.shutu.model.vo.room;

// 1. 引入 Jackson 注解
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
public class RoomVo {

    @Schema(description = "房间id")
    @JsonSerialize(using = ToStringSerializer.class) // 2. 添加注解
    private Long id;

    @Schema(description = "房间类型 1群聊 2私聊")
    private Integer type;

    @Schema(description = "群最后消息的更新时间")
    private Date activeTime;

    @Schema(description = "会话中的最后一条消息")
    private String content;

    @Schema(description = "昵称")
    private String roomName;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "用户 ID (私聊时为对方ID，群聊时为群主ID)")
    @JsonSerialize(using = ToStringSerializer.class) // 3. (最关键) 添加注解
    private Long userId;

    @Schema(description = "未读")
    private int unreadNum = 0;

    @Schema(description = "群聊成员总数")
    private Integer memberCount;
}