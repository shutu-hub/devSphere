package com.shutu.model.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "群信息更新DTO")
public class GroupUpdateRequestDTO {

    @Schema(description = "房间ID", required = true)
    @NotNull
    private Long roomId;

    @Schema(description = "群聊名称", required = true)
    @Length(max = 16, message = "群名不能超过16个字符")
    private String name;

    // 未来可以扩展修改头像等
    // private String avatar;
}