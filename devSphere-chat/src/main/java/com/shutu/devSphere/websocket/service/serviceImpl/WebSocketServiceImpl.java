package com.shutu.devSphere.websocket.service.serviceImpl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shutu.devSphere.common.event.GroupMessageEvent;
import com.shutu.devSphere.common.event.PrivateMessageEvent;
import com.shutu.devSphere.config.ThreadPoolConfig;
import com.shutu.devSphere.model.dto.ws.GroupMessageDTO;
import com.shutu.devSphere.model.dto.ws.PrivateMessageDTO;
import com.shutu.devSphere.model.entity.UserRoomRelate;
import com.shutu.devSphere.model.enums.chat.MessageTypeEnum;
import com.shutu.devSphere.model.vo.message.ChatMessageVo;
import com.shutu.devSphere.model.vo.ws.request.WSBaseReq;
import com.shutu.devSphere.model.vo.ws.response.ChatMessageResp;
import com.shutu.devSphere.model.vo.ws.response.WSBaseResp;
import com.shutu.devSphere.service.UserRoomRelateService;
import com.shutu.devSphere.websocket.adapter.WSAdapter;
import com.shutu.devSphere.websocket.service.WebSocketService;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Description: websocket处理类
 * 管理连接、上线/下线、消息分发（私聊、群聊）、事件发布
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final ApplicationEventPublisher applicationEventPublisher;
    @Qualifier(ThreadPoolConfig.WS_EXECUTOR)
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    private final WSAdapter wsAdapter;
    private final UserRoomRelateService userRoomRelateService;

    /**
     * 所有已连接的websocket连接列表和用户 id
     */
    private static final ConcurrentHashMap<Channel,Long> ONLINE_WS_MAP = new ConcurrentHashMap<>();

    /**
     * 所有在线的用户和对应的socket(用户可以多端登录，对应了不同的socket)
     */
    private static final ConcurrentHashMap<Long, CopyOnWriteArrayList<Channel>> ONLINE_UID_MAP = new ConcurrentHashMap<>();


    /**
     * 注册新连接
     * @param channel 渠道
     */
    @Override
    public void connect(Channel channel) {
        Long userId = channel.attr(USER_ID_KEY).get();
        ONLINE_WS_MAP.put(channel,userId);
    }


    /**
     * 用户断开连接时移除 channel
     * @param channel 渠道
     */
    @Override
    public void removed(Channel channel) {
        ONLINE_WS_MAP.remove(channel);
    }


    /**
     * 在线发送给所有人
     *
     * @param wsBaseResp WS基础研究
     * @param skipUid    跳过 UID
     */
    @Override
    public void sendToAllOnline(WSBaseResp<?> wsBaseResp, Long skipUid) {

    }


    @Override
    public void sendToAllOnline(WSBaseResp<?> wsBaseResp) {

    }


    /**
     * 给某个用户发送信息
     * @param wsBaseResp
     * @param uid
     */
    @Override
    public void sendToUid(WSBaseResp<?> wsBaseResp, Long uid) {
        CopyOnWriteArrayList<Channel> channels = ONLINE_UID_MAP.get(uid);
        if (CollUtil.isEmpty(channels)) {
            log.info("用户：{}不在线", uid);
            return;
        }
        channels.forEach(channel -> threadPoolTaskExecutor.execute(() -> sendMsg(channel, wsBaseResp)));
    }


    /**
     * 发送聊天信息
     * @param channel
     * @param req
     */
    @Override
    public void sendMessage(Channel channel, WSBaseReq req) {
        String msg = req.getData();
        ChatMessageVo chatMessage = JSONUtil.toBean(msg, ChatMessageVo.class);
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.of(chatMessage.getType());
        switch (messageTypeEnum) {
            case PRIVATE:
                // 私聊
                // 1 创建一个私聊事件，持久化信息到数据库
                PrivateMessageDTO privateMessageDTO = new PrivateMessageDTO();
                privateMessageDTO.setFromUserId(channel.attr(USER_ID_KEY).get());
                privateMessageDTO.setToUserId(req.getUserId());
                privateMessageDTO.setContent(chatMessage.getContent());
                applicationEventPublisher.publishEvent(new PrivateMessageEvent(this,privateMessageDTO));
                // 2 判断该用户是否在线，在线则直接推送信息
                if (ONLINE_UID_MAP.contains(req.getUserId())){
                    // 用户在线，向多端发送实时信息
                    WSBaseResp<ChatMessageResp> wsBaseResp = wsAdapter.buildPrivateMessageResp(privateMessageDTO);
                    CopyOnWriteArrayList<Channel> channels = ONLINE_UID_MAP.get(req.getUserId());
                    threadPoolTaskExecutor.execute(() -> {
                        for (Channel ch : channels) {
                            sendMsg(ch,wsBaseResp);
                        }
                    });
                }
                break;
            case GROUP:
                // 群聊
                // 1 创建一个群聊事件，持久化信息到数据库
                GroupMessageDTO groupMessageDTO = new GroupMessageDTO();
                groupMessageDTO.setFromUserId(channel.attr(USER_ID_KEY).get());
                groupMessageDTO.setToRoomId(req.getUserId());
                groupMessageDTO.setContent(chatMessage.getContent());
                applicationEventPublisher.publishEvent(new GroupMessageEvent(this,groupMessageDTO));
                // 2 群聊，向所有群成员发送实时信息
                sendGroupMessage(groupMessageDTO);
                break;
        }
    }


    /**
     * 发送群聊信息
     * @param groupMessageDTO
     */
    private void sendGroupMessage(GroupMessageDTO groupMessageDTO) {
        WSBaseResp<ChatMessageResp> baseResp = wsAdapter.buildGroupMessageResp(groupMessageDTO);
        //获取房间人员id数组
        List<UserRoomRelate> list = userRoomRelateService.list(new LambdaQueryWrapper<UserRoomRelate>().eq(UserRoomRelate::getRoomId, groupMessageDTO.getToRoomId()));
        if (list.isEmpty()){
            return;
        }
        list.forEach(userRoomRelate -> {
            Long uid = userRoomRelate.getUserId();
            if (uid.equals(groupMessageDTO.getFromUserId())){
                return;
            }
            sendToUid(baseResp, uid);
        });
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
}
