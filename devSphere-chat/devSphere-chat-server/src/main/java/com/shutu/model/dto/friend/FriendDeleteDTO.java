package com.shutu.model.dto.friend;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "删除好友DTO")
public class FriendDeleteDTO {

    @Schema(description = "好友的用户ID", required = true)
    @NotNull(message = "好友ID不能为空")
    private Long friendId;
}