package com.shutu.common.listener;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shutu.common.event.GroupMessageEvent;
import com.shutu.model.dto.ws.GroupMessageDTO;
import com.shutu.model.entity.Message;
import com.shutu.model.entity.Room;
import com.shutu.model.entity.UserRoomRelate;
import com.shutu.model.enums.chat.MessageStatusEnum;
import com.shutu.model.enums.chat.MessageTypeEnum;
import com.shutu.service.MessageService;
import com.shutu.service.RoomService;
import com.shutu.service.UserRoomRelateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMessageListener {

    private final MessageService messageService;
    private final RoomService roomService;
    private final UserRoomRelateService userRoomRelateService;

    /**
     * 监听群聊消息，异步保存至数据库
     */
    @Async
    @EventListener(classes = GroupMessageEvent.class)
    @Transactional(rollbackFor = Exception.class) // 确保消息保存和房间更新在同一事务中
    public void handleGroupMessage(GroupMessageEvent event) {
        GroupMessageDTO groupMessageDTO = event.getGroupMessageDTO();
        if (groupMessageDTO == null) {
            return;
        }

        try {
            // 1. 创建消息实体
            Message message = new Message();
            message.setRoomId(groupMessageDTO.getToRoomId());
            message.setFromUid(groupMessageDTO.getFromUserId());
            message.setContent(groupMessageDTO.getContent());
            message.setType(MessageTypeEnum.TEXT.getType());
            message.setStatus(MessageStatusEnum.NORMAL.getStatus());

            // 2. 保存消息
            // 确保 save 方法执行后，message 对象会被回填 ID 和 createTime
            messageService.save(message);

            // 3.更新发送者的最新已读消息
            userRoomRelateService.update(
                    new LambdaUpdateWrapper<UserRoomRelate>()
                            .eq(UserRoomRelate::getUserId, message.getFromUid()) // 发送人
                            .eq(UserRoomRelate::getRoomId, message.getRoomId()) // 该房间
                            .set(UserRoomRelate::getLatestReadMsgId, message.getId()) // 更新为刚发送的消息ID
            );

            // 4. 更新会话表的最后活跃信息
            Room room = new Room();
            room.setId(groupMessageDTO.getToRoomId());
            room.setLastMsgId(message.getId());
            room.setActiveTime(message.getCreateTime()); // 使用消息的创建时间
            roomService.updateById(room);

        } catch (Exception e) {
            log.error("群聊消息异步保存失败: {}, 异常: {}", groupMessageDTO, e.getMessage(), e);
        }
    }
}