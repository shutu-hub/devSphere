package com.shutu.common.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shutu.config.RedisStreamConfig;
import com.shutu.model.entity.Message;
import com.shutu.model.entity.Room;
import com.shutu.model.entity.RoomFriend;
import com.shutu.model.entity.UserRoomRelate;
import com.shutu.model.enums.chat.MessageStatusEnum;
import com.shutu.model.enums.chat.MessageTypeEnum;
import com.shutu.model.enums.chat.RoomTypeEnum;
import com.shutu.model.enums.ws.WSReqTypeEnum;
import com.shutu.model.vo.ws.response.ChatMessageResp;
import com.shutu.model.vo.ws.response.WSBaseResp;
import com.shutu.service.MessageService;
import com.shutu.service.RoomFriendService;
import com.shutu.service.RoomService;
import com.shutu.service.UserRoomRelateService;
import com.shutu.websocket.service.WebSocketService;
import com.shutu.websocket.adapter.WSAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;
import java.util.Date;

/**
 * 消息日志消费者
 * 负责：Redis Stream -> MySQL -> WebSocket Push
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final MessageService messageService;
    private final RoomService roomService;
    private final UserRoomRelateService userRoomRelateService;
    private final RoomFriendService roomFriendService;
    private final WebSocketService webSocketService;
    private final WSAdapter wsAdapter;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redisTemplate;

    // 死信队列 Key
    private static final String DLQ_STREAM_KEY = "im:message:dlq";
    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        Map<String, String> value = record.getValue();
        // 去除键名中的空格
        String serverMsgIdStr = value.get("server_msg_id");
        Long serverMsgId = serverMsgIdStr != null ? Long.valueOf(serverMsgIdStr) : null;

        String tempId = value.get("tempId");
        Long fromUserId = Long.valueOf(value.get("fromUserId"));
        String content = value.get("content");
        int type = Integer.parseInt(value.get("type"));
        Long targetId = Long.valueOf(value.get("targetId"));

        // 获取消息内容类型
        String msgTypeStr = value.get("messageType");
        int messageType = msgTypeStr != null ? Integer.parseInt(msgTypeStr) : MessageTypeEnum.TEXT.getType();

        // 1 保证幂等性
        if (serverMsgId != null) {
            Message existed = messageService.getOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getServerMsgId, serverMsgId)
                    .last("limit 1"));
            if (existed != null) {
                log.warn("消息已处理过，执行幂等返回 tempId={}, messageId={}", tempId, existed.getId());

                // 已处理的消息仍然需要推送给当前用户，避免前端卡住
                pushMessage(existed, tempId, type);

                // ACK 掉 Stream 的消息，避免重复消费
                redisTemplate.opsForStream().acknowledge(
                        RedisStreamConfig.IM_STREAM_KEY,
                        RedisStreamConfig.IM_GROUP,
                        record.getId());

                return; // 幂等生效：不重复入库
            }
        }

        log.info("Stream 收到消息: tempId={}, content={}, msgType={}", tempId, content, messageType);

        try {
            // 1. 确定房间ID
            Long roomId = resolveRoomId(type, fromUserId, targetId);
            if (roomId == null) {
                log.error("未找到房间ID，消息可能非法: tempId={}", tempId);
                // 这种业务错误通常无法通过重试解决，这里直接 ACK 结束
                redisTemplate.opsForStream().acknowledge(RedisStreamConfig.IM_STREAM_KEY, RedisStreamConfig.IM_GROUP,
                        record.getId());
                return;
            }

            // 2. 数据库事务: 落库 + 更新状态
            Message savedMessage = transactionTemplate.execute(status -> {
                // 2.1 保存消息
                Message message = new Message();
                message.setRoomId(roomId);
                message.setFromUid(fromUserId);
                message.setContent(content);
                message.setType(messageType);
                message.setStatus(MessageStatusEnum.NORMAL.getStatus());
                if (serverMsgId != null) {
                    message.setServerMsgId(serverMsgId);
                }
                // 手动设置时间，确保 room.setActiveTime 能获取到值
                Date now = new Date();
                message.setCreateTime(now);
                message.setUpdateTime(now);

                messageService.save(message);

                // 2.2 更新发送者已读
                userRoomRelateService.update(new LambdaUpdateWrapper<UserRoomRelate>()
                        .eq(UserRoomRelate::getUserId, fromUserId)
                        .eq(UserRoomRelate::getRoomId, roomId)
                        .set(UserRoomRelate::getLatestReadMsgId, message.getId()));

                // 2.3用户之前将会话隐藏，当收到消息时需更新会话为显示
                userRoomRelateService.update(new LambdaUpdateWrapper<UserRoomRelate>()
                        .eq(UserRoomRelate::getRoomId, roomId)
                        .set(UserRoomRelate::getIsDeleted, 0)); // 全员复活

                // 2.4 更新房间活跃时间
                Room room = new Room();
                room.setId(roomId);
                room.setLastMsgId(message.getId());
                room.setActiveTime(message.getCreateTime());
                roomService.updateById(room);

                return message;
            });

            if (savedMessage == null)
                return;

            // 3. 消息推送
            pushMessage(savedMessage, tempId, type);

            // 4. 手动 ACK：业务处理成功，确认消息，将消息从redisStream中弹出
            redisTemplate.opsForStream().acknowledge(RedisStreamConfig.IM_STREAM_KEY, RedisStreamConfig.IM_GROUP,
                    record.getId());

        } catch (Exception e) {
            log.error("消息消费处理异常: tempId={}", tempId, e);
            // 5. 异常处理与 DLQ 机制
            handleException(record);
        }
    }

    /**
     * 异常处理：检查重试次数，超过限制移入死信队列
     */
    private void handleException(MapRecord<String, String, String> record) {
        try {
            // 查询当前消息在 Pending List 中的详情
            PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                    RedisStreamConfig.IM_STREAM_KEY,
                    Consumer.from(RedisStreamConfig.IM_GROUP, RedisStreamConfig.IM_CONSUMER),
                    Range.just(record.getId().getValue()),
                    1L);

            if (pendingMessages.isEmpty()) {
                return;
            }

            PendingMessage pendingMessage = pendingMessages.get(0);
            long deliveryCount = pendingMessage.getTotalDeliveryCount();

            if (deliveryCount >= MAX_RETRY_COUNT) {
                log.warn("消息重试次数({})超过上限，移入死信队列 DLQ: id={}", deliveryCount, record.getId());

                // 1. 写入死信队列 (保留原消息体)
                redisTemplate.opsForStream().add(DLQ_STREAM_KEY, record.getValue());

                // 2. ACK 原队列消息 (将其移出 PEL，避免死循环)
                redisTemplate.opsForStream().acknowledge(RedisStreamConfig.IM_STREAM_KEY, RedisStreamConfig.IM_GROUP,
                        record.getId());
            } else {
                log.info("消息处理失败，等待重试 (当前次数: {})", deliveryCount);
            }

        } catch (Exception ex) {
            log.error("DLQ 处理逻辑异常", ex);
        }
    }

    /**
     * 解析房间ID
     */
    private Long resolveRoomId(int type, Long fromUserId, Long targetId) {
        if (type == RoomTypeEnum.GROUP.getType()) {
            return targetId;
        } else {
            long minUid = Math.min(fromUserId, targetId);
            long maxUid = Math.max(fromUserId, targetId);
            RoomFriend rf = roomFriendService.getOne(new LambdaQueryWrapper<RoomFriend>()
                    .eq(RoomFriend::getUid1, minUid)
                    .eq(RoomFriend::getUid2, maxUid)
                    .select(RoomFriend::getRoomId));
            return rf != null ? rf.getRoomId() : null;
        }
    }

    /**
     * 推送消息逻辑
     */
    private void pushMessage(Message message, String tempId, int type) {
        ChatMessageResp resp = wsAdapter.buildMessageResp(message, tempId);
        WSBaseResp<ChatMessageResp> wsResp = new WSBaseResp<>();
        wsResp.setType(WSReqTypeEnum.CHAT.getType());
        wsResp.setData(resp);

        // 这里消息已经通过事务保证安全落库，可以告诉前端消息保存完毕，并返回真实的 messageId 和创建时间
        webSocketService.sendToUid(wsResp, message.getFromUid());

        if (type == RoomTypeEnum.PRIVATE.getType()) {
            // 私聊
            List<UserRoomRelate> members = userRoomRelateService.list(new LambdaQueryWrapper<UserRoomRelate>()
                    .eq(UserRoomRelate::getRoomId, message.getRoomId())
                    .ne(UserRoomRelate::getUserId, message.getFromUid()));
            for (UserRoomRelate member : members) {
                webSocketService.sendToUid(wsResp, member.getUserId());
            }
        } else {
            // 群聊
            List<UserRoomRelate> list = userRoomRelateService.list(new LambdaQueryWrapper<UserRoomRelate>()
                    .eq(UserRoomRelate::getRoomId, message.getRoomId()));

            list.forEach(relate -> {
                if (!relate.getUserId().equals(message.getFromUid())) {
                    webSocketService.sendToUid(wsResp, relate.getUserId());
                }
            });
        }
    }
}