package com.shutu.common.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shutu.common.event.PrivateMessageEvent;
import com.shutu.model.dto.ws.PrivateMessageDTO;
import com.shutu.model.entity.Message;
import com.shutu.model.entity.Room;
import com.shutu.model.entity.RoomFriend;
import com.shutu.model.entity.UserRoomRelate;
import com.shutu.model.enums.chat.MessageStatusEnum;
import com.shutu.model.enums.chat.MessageTypeEnum;
import com.shutu.service.MessageService;
import com.shutu.service.RoomFriendService;
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
public class PrivateMessageListener {

    private final MessageService messageService;
    private final RoomService roomService;
    private final RoomFriendService roomFriendService;
    private final UserRoomRelateService userRoomRelateService;

    /**
     * 监听私聊消息，保存至数据库
     * @param event
     */
    @Async
    @EventListener(classes = PrivateMessageEvent.class)
    @Transactional(rollbackFor = Exception.class)
    public void handlePrivateMessage(PrivateMessageEvent event) {
        PrivateMessageDTO privateMessageDTO = event.getPrivateMessageDTO();
        if (privateMessageDTO == null) {
            return;
        }

        try {
            // 1. 查找私聊房间 ID
            Long uid1 = privateMessageDTO.getFromUserId();
            Long uid2 = privateMessageDTO.getToUserId();

            // 确保 uid1 < uid2，以匹配 room_friend 表的 roomKey 规则
            Long smallerUid = Math.min(uid1, uid2);
            Long largerUid = Math.max(uid1, uid2);

            RoomFriend roomFriend = roomFriendService.getOne(
                    new LambdaQueryWrapper<RoomFriend>()
                            .eq(RoomFriend::getUid1, smallerUid)
                            .eq(RoomFriend::getUid2, largerUid)
            );

            if (roomFriend == null) {
                log.error("严重错误：找不到私聊房间, From: {}, To: {}", uid1, uid2);
                // 理论上在发送消息前就应该创建好了房间，这里找不到是异常情况
                return;
            }

            Long roomId = roomFriend.getRoomId();

            // 2. 创建消息实体
            Message message = new Message();
            message.setRoomId(roomId);
            message.setFromUid(privateMessageDTO.getFromUserId());
            message.setContent(privateMessageDTO.getContent());
            message.setType(MessageTypeEnum.TEXT.getType());
            message.setStatus(MessageStatusEnum.NORMAL.getStatus());

            // 3. 保存消息
            messageService.save(message);

            // 4.更新发送者的最新已读消息
            userRoomRelateService.update(
                    new LambdaUpdateWrapper<UserRoomRelate>()
                            .eq(UserRoomRelate::getUserId, message.getFromUid()) // 发送人
                            .eq(UserRoomRelate::getRoomId, message.getRoomId()) // 该房间
                            .set(UserRoomRelate::getLatestReadMsgId, message.getId()) // 更新为刚发送的消息ID
            );

            // 5. 更新会话表的最后活跃信息
            Room room = new Room();
            room.setId(roomId);
            room.setLastMsgId(message.getId());
            room.setActiveTime(message.getCreateTime());
            roomService.updateById(room);

        } catch (Exception e) {
            log.error("私聊消息异步保存失败: {}, 异常: {}", privateMessageDTO, e.getMessage(), e);
        }
    }
}