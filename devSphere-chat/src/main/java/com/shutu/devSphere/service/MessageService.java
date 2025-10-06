package com.shutu.devSphere.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shutu.devSphere.model.dto.chat.MessageQueryRequest;
import com.shutu.devSphere.model.entity.Message;
import com.shutu.devSphere.model.vo.ws.response.ChatMessageResp;

public interface MessageService extends IService<Message> {

    /**
     * 按页面列出消息 VO
     *
     * @param messageQueryRequest 消息查询请求
     * @return {@link Page}<{@link ChatMessageResp}>
     */
    Page<ChatMessageResp> listMessageVoByPage(MessageQueryRequest messageQueryRequest);
}
