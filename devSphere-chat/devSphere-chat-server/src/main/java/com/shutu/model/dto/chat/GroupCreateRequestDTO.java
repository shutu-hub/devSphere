package com.shutu.model.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serializable;
import java.util.List;

/**
 * 创建群聊的请求 DTO
 */
@Data
@Schema(description = "创建群聊请求")
public class GroupCreateRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "群聊名称", required = true)
    @NotBlank(message = "群聊名称不能为空")
    @Length(max = 16, message = "群名不能超过16个字符")
    private String name;

    @Schema(description = "选择的好友ID列表", required = true)
    @NotEmpty(message = "请至少选择两位好友")
    @Size(min = 2, message = "群聊至少需要3人（包括您自己）")
    private List<Long> userIds;
}