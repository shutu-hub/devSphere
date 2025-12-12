package com.shutu.model.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Schema(description = "邀请进群请求")
public class GroupInviteRequestDTO {

    @Schema(description = "房间ID")
    @NotNull(message = "房间ID不能为空")
    private Long roomId;

    @Schema(description = "被邀请的用户ID列表")
    @NotNull(message = "用户ID列表不能为空")
    private List<Long> userIds;
}
