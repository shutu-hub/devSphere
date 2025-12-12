package com.shutu.model.vo.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "群聊详细信息VO")
public class GroupDetailVo {

    @Schema(description = "房间ID")
    private Long roomId;

    @Schema(description = "群聊名称")
    private String name;

    @Schema(description = "群头像")
    private String avatar;

    @Schema(description = "群主ID")
    private Long ownerId;

    @Schema(description = "成员总数")
    private Integer memberCount;

    // (可选) 可以在此接口一次性返回少量成员
    // private List<GroupMemberVo> members; 
}