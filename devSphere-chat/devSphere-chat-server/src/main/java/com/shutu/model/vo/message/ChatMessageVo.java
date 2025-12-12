package com.shutu.model.vo.message;

import lombok.Data;

/**
 * 聊天消息VO
 *
 * @author liuhuaicong
 * @date 2023/10/31
 */
@Data
public class ChatMessageVo {
    /**
     * 消息类型 1、群聊 2、私聊
     */
    private Integer type;
    private String content;
    /**
     * 消息的临时ID，由前端生成，用于ACK确认
     */
    private String tempId;

    /**
     * 消息内容类型 (1:文本, 3:图片, 4:文件, 5:语音通话, 6:视频通话)
     */
    private Integer messageType;
}
