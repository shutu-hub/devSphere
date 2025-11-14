package com.shutu.devSphere.model.dto.friend;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "好友备注更新DTO")
public class FriendRemarkUpdateDTO {

    @Schema(description = "好友的用户ID", required = true)
    @NotNull(message = "好友ID不能为空")
    private Long friendId;

    @Schema(description = "好友备注")
    @Length(max = 50, message = "备注不能超过50个字符")
    private String remark;
}