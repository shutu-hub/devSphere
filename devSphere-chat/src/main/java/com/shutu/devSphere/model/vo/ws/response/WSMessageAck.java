package com.shutu.devSphere.model.vo.ws.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 消息发送确认 (Server ACK)
 * 仅用于告知前端：服务器已收到消息并写入队列，前端可将消息状态置为 "发送中/已发送(未持久化)"
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WSMessageAck {
    /**
     * 前端生成的临时ID
     */
    private String tempId;

    private String serverMsgId;

    private Date serverTs;

    public WSMessageAck(String tempId, String serverMsgId, long serverTs) {
        this.tempId = tempId;
        this.serverMsgId = serverMsgId;
        this.serverTs = new Date(serverTs);
    }
}