package com.shutu.devSphere.model.vo.ws.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * 消息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageResp {
    /**
     * 发送者信息
     */
    private UserInfo fromUser;

    /**
     * 消息详情
     */
    private Message message;

    /**
     * 房间id
     */
    private Long roomId;

    @Data
    public static class UserInfo {
        // 用户名称
        private String username;
        // 用户id
        private Long uid;
        // 头像
        private String avatar;
    }

    @Data
    public static class Message {
        // 消息id
        private Long id;
        // 消息发送时间
        private Date sendTime;
        // 消息内容
        private String content;
        // 消息类型 1正常文本 2.爆赞 （点赞超过10）3.危险发言（举报超5）
        private Integer type;
    }

}
