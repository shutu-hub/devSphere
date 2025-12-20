package com.shutu.websocket.service.serviceImpl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shutu.config.RedisStreamConfig;
import com.shutu.config.ThreadPoolConfig;
import com.shutu.constant.RedisKeyConstant;
import com.shutu.model.dto.chat.RouteMessageDTO;
import com.shutu.model.dto.ws.GroupMessageDTO;
import com.shutu.model.entity.UserRoomRelate;
import com.shutu.model.enums.chat.RoomTypeEnum;
import com.shutu.model.enums.ws.WSReqTypeEnum;
import com.shutu.model.vo.message.ChatMessageVo;
import com.shutu.model.vo.ws.request.WSBaseReq;
import com.shutu.model.vo.ws.response.ChatMessageResp;
import com.shutu.model.vo.ws.response.WSBaseResp;
import com.shutu.model.vo.ws.response.WSErrorResp;
import com.shutu.model.vo.ws.response.WSMessageAck;
import com.shutu.service.UserLocationService;
import com.shutu.service.UserRoomRelateService;
import com.shutu.websocket.service.WebSocketService;
import com.shutu.websocket.adapter.WSAdapter;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * websocket处理类
 * 管理连接、上线/下线、消息分发（私聊、群聊）、事件发布
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final StringRedisTemplate redisTemplate;
    private final UserLocationService userLocationService;
    @Qualifier(ThreadPoolConfig.WS_EXECUTOR)
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    private final WSAdapter wsAdapter;
    private final UserRoomRelateService userRoomRelateService;

    /**
     * 所有已连接的websocket连接列表和用户 id
     */
    private static final ConcurrentHashMap<Channel, Long> ONLINE_WS_MAP = new ConcurrentHashMap<>();

    /**
     * 所有在线的用户和对应的socket(用户可以多端登录，对应了不同的socket)
     */
    private static final ConcurrentHashMap<Long, CopyOnWriteArrayList<Channel>> ONLINE_UID_MAP = new ConcurrentHashMap<>();

    public void connect(Channel channel) {
        Long userId = channel.attr(USER_ID_KEY).get();
        // 1. 维护 Channel -> UserId 映射
        ONLINE_WS_MAP.put(channel, userId);
        // 2. 维护 UserId -> Channel[] 映射
        ONLINE_UID_MAP.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(channel);
        // 3. 注册用户位置到 Redis (User -> NodeID)
        userLocationService.register(userId);

        log.info("用户上线: {}, 当前在线人数: {}", userId, ONLINE_UID_MAP.size());
    }

    public void removed(Channel channel) {
        Long userId = ONLINE_WS_MAP.get(channel);
        if (userId != null) {
            // 1. 移除 Channel -> UserId 映射
            ONLINE_WS_MAP.remove(channel);
            // 2. 移除 UserId -> Channel[] 映射
            CopyOnWriteArrayList<Channel> channels = ONLINE_UID_MAP.get(userId);
            if (channels != null) {
                channels.remove(channel);
                // 如果该用户没有其他连接了，从大 Map 中移除
                if (channels.isEmpty()) {
                    ONLINE_UID_MAP.remove(userId);
                    // 3. 从 Redis 移除用户位置
                    userLocationService.remove(userId);
                }
            }
            log.info("用户下线: {}", userId);
        }
    }

    /**
     * 在线发送给所有人
     *
     * @param wsBaseResp WS基础研究
     * @param skipUid    跳过 UID
     */
    @Override
    public void sendToAllOnline(WSBaseResp<?> wsBaseResp, Long skipUid) {
        ONLINE_WS_MAP.forEach((channel, uid) -> {
            if (skipUid != null && Objects.equals(uid, skipUid)) {
                return; // 跳过指定用户
            }
            threadPoolTaskExecutor.execute(() -> sendMsg(channel, wsBaseResp));
        });
    }

    @Override
    public void sendToAllOnline(WSBaseResp<?> wsBaseResp) {
        sendToAllOnline(wsBaseResp, null);
    }

    /**
     * 给某个用户发送信息 (核心路由逻辑)
     */
    @Override
    public void sendToUid(WSBaseResp<?> wsBaseResp, Long uid) {
        // 1. 优先检查本地是否在线
        if (ONLINE_UID_MAP.containsKey(uid)) {
            sendToLocalUid(wsBaseResp, uid);
            return;
        }

        // 2. 本地不在线，查询 Redis 路由信息
        String targetNodeId = userLocationService.getNode(uid);
        if (targetNodeId != null) {
            if (userLocationService.isLocal(targetNodeId)) {
                // 极端情况：Redis 说是本机，但刚才 Map 没查到 -> 说明刚下线或脏数据，忽略
                return;
            }

            // 3. 目标在远程节点，进行 Pub/Sub 广播
            // 封装路由消息
            RouteMessageDTO routeMsg = RouteMessageDTO.builder()
                    .targetUid(uid)
                    .messageJson(JSONUtil.toJsonStr(wsBaseResp))
                    .build();

            // 发送到目标节点的专属 Topic
            String topic = RedisKeyConstant.TOPIC_NODE_ROUTE_PREFIX + targetNodeId;
            redisTemplate.convertAndSend(topic, JSONUtil.toJsonStr(routeMsg));
            log.debug("消息路由转发: uid={}, targetNode={}", uid, targetNodeId);
        } else {
            // 用户彻底离线，离线消息已落库，无需处理
            log.debug("用户离线，无需推送: uid={}", uid);
        }
    }

    /**
     * 仅推送到本机用户
     */
    @Override
    public void sendToLocalUid(WSBaseResp<?> wsBaseResp, Long uid) {
        CopyOnWriteArrayList<Channel> channels = ONLINE_UID_MAP.get(uid);
        if (CollUtil.isEmpty(channels)) {
            return;
        }
        // 推送给该用户的所有在线设备
        channels.forEach(channel -> threadPoolTaskExecutor.execute(() -> sendMsg(channel, wsBaseResp)));
    }


    @Override
    public void sendMessage(Channel channel, WSBaseReq req) {
        String msg = req.getData();
        ChatMessageVo chatMessage = JSONUtil.toBean(msg, ChatMessageVo.class);
        String tempId = chatMessage.getTempId();

        if (tempId == null)
            return;

        Long fromUserId = channel.attr(USER_ID_KEY).get();
        RoomTypeEnum roomType = RoomTypeEnum.of(chatMessage.getType());

        // 1. 准备存入 Redis Stream 的消息体
        String serverMsgId = String.valueOf(IdWorker.getId()); // MyBatis-Plus 的雪花算法
        long serverTs = System.currentTimeMillis();
        // 2. 构建 stream map
        Map<String, String> streamMessage = new HashMap<>();
        streamMessage.put("server_msg_id", serverMsgId);
        streamMessage.put("tempId", tempId);
        streamMessage.put("fromUserId", String.valueOf(fromUserId));
        streamMessage.put("content", chatMessage.getContent());
        streamMessage.put("type", String.valueOf(roomType.getType()));
        // 传递消息内容类型 (默认文本)
        Integer msgType = chatMessage.getMessageType();
        streamMessage.put("messageType", String.valueOf(msgType != null ? msgType : 1));
        streamMessage.put("createTime", String.valueOf(serverTs));

        // 根据类型放入目标ID
        if (roomType == RoomTypeEnum.PRIVATE) {
            streamMessage.put("targetId", String.valueOf(req.getUserId())); // 对方UID
        } else {
            streamMessage.put("targetId", String.valueOf(req.getUserId())); // 房间ID
        }

        try {
            // 2.写入 Redis Stream 顺序写，极快
            RecordId recordId = redisTemplate.opsForStream().add(
                    RedisStreamConfig.IM_STREAM_KEY,
                    streamMessage);
            log.info("消息写入 Redis Stream 成功, StreamId: {}, TempId: {}", recordId, tempId);

            // 3.返回 ACK给发送者，这里的ACK只是告诉前端后端已经拿到数据，不需要重试发送消息
            // 此时数据库还没落库，但我们已通过 Redis 保证了可靠性
            WSMessageAck ackData = new WSMessageAck(tempId, serverMsgId, serverTs);
            sendAck(channel, ackData);
        } catch (Exception e) {
            log.error("消息写入 Redis 失败", e);
            sendError(channel, tempId, "服务器繁忙");
        }
    }

    @Override
    public void handleRtcSignal(Channel channel, WSBaseReq req) {
        Long targetUid = req.getUserId();
        if (targetUid == null)
            return;

        // 构造响应体
        WSBaseResp<String> resp = new WSBaseResp<>();
        resp.setType(WSReqTypeEnum.RTC_SIGNAL.getType());
        resp.setData(req.getData()); // 透传信令数据

        // 发送给目标用户
        sendToUid(resp, targetUid);
    }

    /**
     * 发送快速 ACK (只确认服务器已接收)
     */
    private void sendAck(Channel channel, WSMessageAck ackData) {
        WSBaseResp<WSMessageAck> wsBaseResp = new WSBaseResp<>();
        wsBaseResp.setType(WSReqTypeEnum.ACK.getType());
        wsBaseResp.setData(ackData);

        sendMsg(channel, wsBaseResp);
    }

    /**
     * 发送群聊信息
     * 
     * @param groupMessageDTO
     */
    private void sendGroupMessage(GroupMessageDTO groupMessageDTO) {
        WSBaseResp<ChatMessageResp> baseResp = wsAdapter.buildGroupMessageResp(groupMessageDTO);
        // 获取房间人员id数组
        List<UserRoomRelate> list = userRoomRelateService.list(
                new LambdaQueryWrapper<UserRoomRelate>().eq(UserRoomRelate::getRoomId, groupMessageDTO.getToRoomId()));
        if (list.isEmpty()) {
            return;
        }
        list.forEach(userRoomRelate -> {
            Long uid = userRoomRelate.getUserId();
            if (uid.equals(groupMessageDTO.getFromUserId())) {
                return;
            }
            sendToUid(baseResp, uid);
        });
    }

    /**
     * 发送错误通知
     * 如果写入 Redis 失败，通知前端将消息状态置为失败
     *
     * @param channel Netty Channel
     * @param tempId  前端生成的临时ID
     * @param msg     错误描述
     */
    /**
     * 发送错误通知
     * 如果写入 Redis 失败，通知前端将消息状态置为失败
     *
     * @param channel Netty Channel
     * @param tempId  前端生成的临时ID
     * @param msg     错误描述
     */
    @SuppressWarnings("unused")
    private void sendError(Channel channel, String tempId, String msg) {
        log.warn("向客户端发送错误通知: tempId={}, msg={}", tempId, msg);

        // 1. 构建错误响应体
        WSErrorResp errorData = new WSErrorResp(tempId, msg);

        // 2. 封装 WS 协议包
        WSBaseResp<WSErrorResp> wsBaseResp = new WSBaseResp<>();
        wsBaseResp.setType(WSReqTypeEnum.ERROR.getType());
        wsBaseResp.setData(errorData);

        // 3. 发送
        sendMsg(channel, wsBaseResp);
    }

    /**
     * 用户上线
     */
    private void online(Channel channel, Long uid) {

    }

    /**
     * 发送消息
     *
     * @param channel    渠道
     * @param wsBaseResp WS基础研究
     */
    private void sendMsg(Channel channel, WSBaseResp<?> wsBaseResp) {
        channel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(wsBaseResp)));
    }

    /**
     * 用户下线
     * return 是否全下线成功
     */
    private boolean offline(Channel channel, Optional<Long> uidOptional) {
        return true;
    }

    /**
     * 心跳检测
     *
     * @param channel
     */
    @Override
    public void heartbeat(Channel channel) {
        Long userId = channel.attr(USER_ID_KEY).get();
        if (userId != null) {
            log.debug("收到用户的心跳包: {}", userId);
            userLocationService.register(userId);
        }
    }
}
