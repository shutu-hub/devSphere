package com.shutu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shutu.model.dto.chat.CursorPage;
import com.shutu.model.dto.chat.MessageQueryRequest;
import com.shutu.model.entity.Message;
import com.shutu.model.vo.ws.response.ChatMessageResp;

import java.util.List;

public interface MessageService extends IService<Message> {

    /**
     * 按页面列出消息 VO
     *
     * @param messageQueryRequest 消息查询请求
     * @return {@link Page}<{@link ChatMessageResp}>
     */
    CursorPage<ChatMessageResp> listMessageVoByPage(MessageQueryRequest messageQueryRequest);

    void markConversationAsRead(Long roomId);

    /**
     * 搜索聊天记录
     * 
     * @param roomId  房间ID
     * @param keyword 关键词
     * @return 消息列表
     */
    List<ChatMessageResp> searchHistory(Long roomId, String keyword);

    /**
     * 撤回消息
     * 
     * @param messageId 消息ID
     */
    void recallMessage(Long messageId);
}
