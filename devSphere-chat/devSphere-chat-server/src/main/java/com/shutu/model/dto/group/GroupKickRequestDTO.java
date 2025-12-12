package com.shutu.model.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "移出群成员请求")
public class GroupKickRequestDTO {

    @Schema(description = "房间ID")
    @NotNull(message = "房间ID不能为空")
    private Long roomId;

    @Schema(description = "被移出的用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long uid;
}
