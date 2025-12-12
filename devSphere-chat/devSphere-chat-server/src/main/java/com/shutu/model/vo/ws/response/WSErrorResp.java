package com.shutu.model.vo.ws.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 错误响应实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WSErrorResp {
    /**
     * 前端生成的临时ID (关键：用于前端将 loading 状态改为 error 状态)
     */
    private String tempId;

    /**
     * 错误原因 (例如：服务器繁忙、Redis连接失败等)
     */
    private String reason;
}