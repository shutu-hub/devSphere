package com.shutu.model.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;


/**
 * 聊天查询请求
 */
@Data
public class MessageQueryRequest {
    /**
     * 房间 ID
     */
    private Long roomId;

    @Schema(description = "游标。查询此消息ID之前的记录。如果是第一次查询（查最新的），则不传或传null。")
    private String cursor;

    @Schema(description = "每页大小，默认20")
    private Integer pageSize = 20;
}
