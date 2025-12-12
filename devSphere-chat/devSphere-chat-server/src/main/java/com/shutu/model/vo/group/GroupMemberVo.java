package com.shutu.model.vo.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "群成员VO")
public class GroupMemberVo {

    @Schema(description = "用户ID")
    private Long uid;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "是否为群主 (0=否, 1=是)")
    private Integer isOwner = 0;
}