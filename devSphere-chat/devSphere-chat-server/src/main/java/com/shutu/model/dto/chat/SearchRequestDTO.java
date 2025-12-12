package com.shutu.model.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "全局搜索请求")
public class SearchRequestDTO {

    @NotBlank(message = "搜索关键词不能为空")
    @Schema(description = "搜索词（用户名或群聊ID）")
    private String query;
}