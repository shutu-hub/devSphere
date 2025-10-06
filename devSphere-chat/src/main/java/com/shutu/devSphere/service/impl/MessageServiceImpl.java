package com.shutu.devSphere.service.impl;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shutu.devSphere.mapper.MessageMapper;
import com.shutu.devSphere.model.dto.chat.MessageQueryRequest;
import com.shutu.devSphere.model.entity.Message;
import com.shutu.devSphere.model.vo.ws.response.ChatMessageResp;
import com.shutu.devSphere.service.MessageService;
import com.shutu.devSphere.websocket.adapter.WSAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
        implements MessageService {

    private final WSAdapter wsAdapter;

    @Override
    public Page<ChatMessageResp> listMessageVoByPage(MessageQueryRequest messageQueryRequest) {
        Long roomId = messageQueryRequest.getRoomId();

        // 获取当前页码
        int current = messageQueryRequest.getCurrent();
        // 获取每页大小
        int size = messageQueryRequest.getPageSize();
        if (roomId == null) {
            // 创建新的分页对象，用于存储转换后的消息对象
            Page<ChatMessageResp> messageVoPage = new Page<>(0, size, 0);
            // 将转换后的消息对象列表设置为新的分页对象的记录
            messageVoPage.setRecords(null);
            return messageVoPage;
        }
        // 创建分页对象
        Page<Message> messagePage = this.page(new Page<>(current, size),
                // 创建查询条件对象
                new LambdaQueryWrapper<Message>().eq(Message::getRoomId, roomId).orderByDesc(Message::getCreateTime));
        // 获取分页结果中的消息列表 翻转
        List<Message> messageList = ListUtil.reverse(messagePage.getRecords());
        // 将消息列表转换为ChatMessageResp对象列表
        List<ChatMessageResp> chatMessageRespList = messageList.stream().map(item -> wsAdapter.getMessageVo(item.getContent()))
                .collect(Collectors.toList());
        // 创建新的分页对象，用于存储转换后的消息对象
        Page<ChatMessageResp> messageVoPage = new Page<>(current, size, messagePage.getTotal());
        // 将转换后的消息对象列表设置为新的分页对象的记录
        messageVoPage.setRecords(chatMessageRespList);
        // 返回新的分页对象
        return messageVoPage;
    }
}




