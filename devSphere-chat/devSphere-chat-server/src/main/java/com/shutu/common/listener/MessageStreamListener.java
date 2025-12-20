package com.shutu.common.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shutu.config.NodeConfig;
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
    private final NodeConfig nodeConfig;

    // 死信队列 Key
    private static final String DLQ_STREAM_KEY = "im:message:dlq";
    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        Map<String, String> value = record.getValue();

        // 1. 解析消息
        String serverMsgIdStr = value.get("server_msg_id");
        Long serverMsgId = serverMsgIdStr != null ? Long.valueOf(serverMsgIdStr) : null;
        String tempId = value.get("tempId");
        Long fromUserId = Long.valueOf(value.get("fromUserId"));
        String content = value.get("content");
        int type = Integer.parseInt(value.get("type"));
        Long targetId = Long.valueOf(value.get("targetId"));
        String msgTypeStr = value.get("messageType");
        int messageType = msgTypeStr != null ? Integer.parseInt(msgTypeStr) : MessageTypeEnum.TEXT.getType();

        // 2. 幂等性检查
        if (checkIdempotency(serverMsgId, tempId, type, record.getId())) {
            return;
        }

        log.info("[Stream消费] 收到消息: tempId={}, content={}, msgType={}", tempId, content, messageType);

        try {
            // 3. 确定房间ID
            Long roomId = resolveRoomId(type, fromUserId, targetId);
            if (roomId == null) {
                log.error("[Stream消费] 未找到房间ID，消息非法: tempId={}", tempId);
                // 无法修复的业务错误，直接ACK
                ackMessage(record.getId());
                return;
            }

            // 4. 数据库事务处理
            Message savedMessage = saveMessageTransaction(roomId, fromUserId, targetId, content, messageType,
                    serverMsgId, tempId);
            if (savedMessage == null) {
                return; // 事务失败或已处理
            }

            // 5. 推送 WebSocket
            pushMessage(savedMessage, tempId, type);

            // 6. 写入 Redis 缓存 (Write-Through)
            writeToCache(roomId, savedMessage);

            // 7. ACK 确认
            ackMessage(record.getId());

        } catch (Exception e) {
            log.error("[Stream消费] 处理异常: tempId={}", tempId, e);
            // 8. 异常处理与 DLQ
            handleException(record);
        }
    }

    /**
     * 幂等性检查
     * 优先使用 tempId (客户端防抖)，其次兼容 serverMsgId (旧数据)
     * 
     * @return true 表示已处理过（幂等生效），false 表示未处理
     */
    private boolean checkIdempotency(Long serverMsgId, String tempId, int type,
            org.springframework.data.redis.connection.stream.RecordId recordId) {
        // 1. 优先检查 tempId (最准确的客户端幂等)
        if (tempId != null) {
            Message existed = messageService.getOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getTempId, tempId)
                    .last("limit 1"));
            if (existed != null) {
                log.warn("[幂等检查] 此 tempId 已消费，执行跳过: tempId={}, messageId={}", tempId, existed.getId());
                pushMessage(existed, tempId, type);
                ackMessage(recordId);
                return true;
            }
        }

        // 2. 兜底检查 serverMsgId (以防万一 tempId 丢失或旧版本消息)
        if (serverMsgId != null) {
            Message existed = messageService.getOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getServerMsgId, serverMsgId)
                    .last("limit 1"));
            if (existed != null) {
                log.warn("[幂等检查] 此 serverMsgId 已消费，执行跳过: serverMsgId={}, messageId={}", serverMsgId, existed.getId());
                pushMessage(existed, tempId, type);
                ackMessage(recordId);
                return true;
            }
        }
        return false;
    }

    /**
     * 数据库事务：保存消息 + 更新会话状态
     */
    private Message saveMessageTransaction(Long roomId, Long fromUserId, Long targetId, String content, int messageType,
            Long serverMsgId, String tempId) {
        return transactionTemplate.execute(status -> {
            try {
                // 1. 保存消息主体
                Message message = new Message();
                message.setRoomId(roomId);
                message.setFromUid(fromUserId);
                message.setContent(content);
                message.setType(messageType);
                message.setStatus(MessageStatusEnum.NORMAL.getStatus());
                if (serverMsgId != null) {
                    message.setServerMsgId(serverMsgId);
                }
                if (tempId != null) {
                    message.setTempId(tempId);
                }
                Date now = new Date();
                message.setCreateTime(now);
                message.setUpdateTime(now);
                messageService.save(message);

                // 2. 更新发送者已读位置
                userRoomRelateService.update(new LambdaUpdateWrapper<UserRoomRelate>()
                        .eq(UserRoomRelate::getUserId, fromUserId)
                        .eq(UserRoomRelate::getRoomId, roomId)
                        .set(UserRoomRelate::getLatestReadMsgId, message.getId()));

                // 3. 激活被隐藏的会话
                userRoomRelateService.update(new LambdaUpdateWrapper<UserRoomRelate>()
                        .eq(UserRoomRelate::getRoomId, roomId)
                        .set(UserRoomRelate::getIsDeleted, 0));

                // 4. 更新房间活跃时间
                Room room = new Room();
                room.setId(roomId);
                room.setLastMsgId(message.getId());
                room.setActiveTime(message.getCreateTime());
                roomService.updateById(room);

                return message;
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("[事务失败] 消息落库失败: roomId={}, from={}", roomId, fromUserId, e);
                throw e; // 抛出异常触发外层重试
            }
        });
    }

    /**
     * 写入 Redis ZSet 缓存
     */
    private void writeToCache(Long roomId, Message message) {
        try {
            String cacheKey = com.shutu.constant.RedisKeyConstant.IM_ROOM_MSG_KEY + roomId;
            String json = cn.hutool.json.JSONUtil.toJsonStr(message);
            // Add to ZSet
            redisTemplate.opsForZSet().add(cacheKey, json, message.getId());
            // 保留最新的 200 条
            redisTemplate.opsForZSet().removeRange(cacheKey, 0, -201);
            // 自动延期 7 天
            redisTemplate.expire(cacheKey, java.time.Duration.ofDays(7));
        } catch (Exception e) {
            log.error("[Cache写入] 失败: roomId={}", roomId, e);
        }
    }

    /**
     * 确认消息 (ACK)
     */
    private void ackMessage(org.springframework.data.redis.connection.stream.RecordId recordId) {
        redisTemplate.opsForStream().acknowledge(
                RedisStreamConfig.IM_STREAM_KEY,
                RedisStreamConfig.IM_GROUP,
                recordId);
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
     * 异常处理与死信队列
     */
    private void handleException(MapRecord<String, String, String> record) {
        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                    RedisStreamConfig.IM_STREAM_KEY,
                    Consumer.from(RedisStreamConfig.IM_GROUP, nodeConfig.getConsumerName()),
                    Range.just(record.getId().getValue()),
                    1L);

            if (pendingMessages.isEmpty()) {
                return;
            }

            PendingMessage pendingMessage = pendingMessages.get(0);
            long deliveryCount = pendingMessage.getTotalDeliveryCount();

            if (deliveryCount >= MAX_RETRY_COUNT) {
                log.warn("[DLQ] 消息重试超限({}), 移入死信: id={}", deliveryCount, record.getId());

                // 1. 写入死信队列
                redisTemplate.opsForStream().add(DLQ_STREAM_KEY, record.getValue());

                // 2. ACK 原消息
                ackMessage(record.getId());
            } else {
                log.info("[重试等待] 消息处理失败,当前次数: {}", deliveryCount);
            }

        } catch (Exception ex) {
            log.error("[DLQ] 处理逻辑异常", ex);
        }
    }

    /**
     * 推送消息
     */
    private void pushMessage(Message message, String tempId, int type) {
        try {
            ChatMessageResp resp = wsAdapter.buildMessageResp(message, tempId);
            WSBaseResp<ChatMessageResp> wsResp = new WSBaseResp<>();
            wsResp.setType(WSReqTypeEnum.CHAT.getType());
            wsResp.setData(resp);

            webSocketService.sendToUid(wsResp, message.getFromUid());

            if (type == RoomTypeEnum.PRIVATE.getType()) {
                List<UserRoomRelate> members = userRoomRelateService.list(new LambdaQueryWrapper<UserRoomRelate>()
                        .eq(UserRoomRelate::getRoomId, message.getRoomId())
                        .ne(UserRoomRelate::getUserId, message.getFromUid()));
                for (UserRoomRelate member : members) {
                    webSocketService.sendToUid(wsResp, member.getUserId());
                }
            } else {
                List<UserRoomRelate> list = userRoomRelateService.list(new LambdaQueryWrapper<UserRoomRelate>()
                        .eq(UserRoomRelate::getRoomId, message.getRoomId()));
                list.forEach(relate -> {
                    if (!relate.getUserId().equals(message.getFromUid())) {
                        webSocketService.sendToUid(wsResp, relate.getUserId());
                    }
                });
            }
        } catch (Exception e) {
            log.error("[推送消息] 失败: roomId={}", message.getRoomId(), e);
        }
    }
}