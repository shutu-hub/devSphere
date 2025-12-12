package com.shutu.websocket.adapter;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shutu.commons.security.cache.TokenStoreCache;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.utils.Result;
import com.shutu.model.dto.ws.GroupMessageDTO;
import com.shutu.model.dto.ws.PrivateMessageDTO;
import com.shutu.model.entity.Message;
import com.shutu.model.entity.RoomFriend;
import com.shutu.model.enums.ws.WSReqTypeEnum;
import com.shutu.model.vo.ws.response.ChatMessageResp;
import com.shutu.model.vo.ws.response.WSBaseResp;
import com.shutu.service.RoomFriendService;
import com.shutu.feign.UserFeignClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * ws适配器
 * 将后端接收到的私聊 / 群聊消息封装成统一的响应对象 WSBaseResp<ChatMessageResp> 发送给前端。
 */
@Component
public class WSAdapter {
    @Resource
    private RoomFriendService roomFriendService;
    @Resource
    private UserFeignClient userFeignClient;

    public TokenStoreCache getTokenStoreCache() {
        return SpringUtil.getBean(TokenStoreCache.class);
    }

    /**
     * 构建私聊消息响应对象
     * 
     * @param privateMessageDTO
     * @return
     */
    public WSBaseResp<ChatMessageResp> buildPrivateMessageResp(PrivateMessageDTO privateMessageDTO) {
        Long loginUserId = privateMessageDTO.getFromUserId();
        Message message = new Message();
        message.setFromUid(loginUserId);// 设置信息的发送者
        message.setContent(privateMessageDTO.getContent());
        message.setCreateTime(new Date());
        ChatMessageResp chatMessageResp = getMessageVo(message);

        // 创建WSBaseResp对象
        WSBaseResp<ChatMessageResp> wsBaseResp = new WSBaseResp<>();
        // 设置房间ID （好友私聊房间）
        Long toUserId = privateMessageDTO.getToUserId();
        long uid1 = loginUserId > toUserId ? toUserId : loginUserId;
        long uid2 = loginUserId > toUserId ? loginUserId : toUserId;
        RoomFriend roomFriend = roomFriendService.getOne(new LambdaQueryWrapper<RoomFriend>()
                .eq(RoomFriend::getUid1, uid1).eq(RoomFriend::getUid2, uid2));
        if (roomFriend != null) {
            chatMessageResp.setRoomId(roomFriend.getRoomId());
        }
        // 设置数据和类型
        wsBaseResp.setData(chatMessageResp);
        wsBaseResp.setType(WSReqTypeEnum.CHAT.getType());
        return wsBaseResp;
    }

    /**
     * 构建群聊消息响应对象
     * 
     * @param groupMessageDTO
     * @return
     */
    public WSBaseResp<ChatMessageResp> buildGroupMessageResp(GroupMessageDTO groupMessageDTO) {
        Message message = new Message();
        message.setRoomId(groupMessageDTO.getToRoomId());
        message.setFromUid(groupMessageDTO.getFromUserId());// 设置信息的发送者
        message.setContent(groupMessageDTO.getContent());
        message.setCreateTime(new Date());
        ChatMessageResp chatMessageResp = getMessageVo(message);

        // 创建WSBaseResp对象
        WSBaseResp<ChatMessageResp> wsBaseResp = new WSBaseResp<>();
        // 设置房间ID
        chatMessageResp.setRoomId(groupMessageDTO.getToRoomId());
        // 设置数据和类型
        wsBaseResp.setData(chatMessageResp);
        wsBaseResp.setType(WSReqTypeEnum.CHAT.getType());
        return wsBaseResp;
    }

    /**
     * 构建消息内容及用户信息结构体 (基础方法)
     * 
     * @param message 消息实体
     * @return ChatMessageResp
     */
    @NotNull
    public ChatMessageResp getMessageVo(Message message) {
        // 创建ChatMessageResp对象
        ChatMessageResp chatMessageResp = new ChatMessageResp();
        // 获取登录用户的信息
        Result<UserDetail> result = userFeignClient.getById(message.getFromUid());
        UserDetail user = result.getData();
        // 创建UserInfo对象
        ChatMessageResp.UserInfo userInfo = new ChatMessageResp.UserInfo();
        if (user != null) {
            // 设置用户名、ID和头像
            userInfo.setUsername(user.getUsername());
            userInfo.setUid(user.getId());
            userInfo.setAvatar(user.getHeadUrl());
        } else {
            // 兜底逻辑
            userInfo.setUid(message.getFromUid());
            userInfo.setUsername("未知用户");
            userInfo.setAvatar("");
        }

        // 和发送者信息
        chatMessageResp.setFromUser(userInfo);
        // 创建Message对象
        ChatMessageResp.Message messageVO = new ChatMessageResp.Message();

        // 设置 ID
        messageVO.setId(message.getId());

        // 设置私信内容
        messageVO.setContent(message.getContent());
        messageVO.setSendTime(message.getCreateTime());
        // 设置消息类型
        messageVO.setType(message.getType());

        // 设置消息对象
        chatMessageResp.setMessage(messageVO);

        // 设置房间ID
        chatMessageResp.setRoomId(message.getRoomId());

        return chatMessageResp;
    }

    /**
     * 从 Message 实体 + tempId 构建响应
     * 
     * @param message 消息实体 (已持久化，包含真实的ID和时间)
     * @param tempId  临时ID (用于前端ACK)
     * @return ChatMessageResp
     */
    @NotNull
    public ChatMessageResp buildMessageResp(Message message, String tempId) {
        // 复用 getMessageVo 构建基础信息
        ChatMessageResp resp = getMessageVo(message);
        // 设置 tempId
        resp.setTempId(tempId);
        return resp;
    }

    /**
     * 批量构建消息响应 (优化 N+1 问题)
     * 
     * @param message       消息实体
     * @param userDetailMap 用户信息映射表
     * @return ChatMessageResp
     */
    public ChatMessageResp buildBatchMessageResp(Message message, java.util.Map<Long, UserDetail> userDetailMap) {
        // 创建ChatMessageResp对象
        ChatMessageResp chatMessageResp = new ChatMessageResp();

        // 从 Map 中获取用户信息，避免 RPC 调用
        UserDetail user = userDetailMap.get(message.getFromUid());

        // 创建UserInfo对象
        ChatMessageResp.UserInfo userInfo = new ChatMessageResp.UserInfo();
        if (user != null) {
            // 设置用户名、ID和头像
            userInfo.setUsername(user.getUsername());
            userInfo.setUid(user.getId());
            userInfo.setAvatar(user.getHeadUrl());
        } else {
            // 兜底逻辑
            userInfo.setUid(message.getFromUid());
            userInfo.setUsername("未知用户");
            userInfo.setAvatar("");
        }

        // 和发送者信息
        chatMessageResp.setFromUser(userInfo);
        // 创建Message对象
        ChatMessageResp.Message messageVO = new ChatMessageResp.Message();

        // 设置 ID
        messageVO.setId(message.getId());

        // 设置私信内容
        messageVO.setContent(message.getContent());
        messageVO.setSendTime(message.getCreateTime());
        // 设置消息类型
        messageVO.setType(message.getType());

        // 设置消息对象
        chatMessageResp.setMessage(messageVO);

        // 设置房间ID
        chatMessageResp.setRoomId(message.getRoomId());

        return chatMessageResp;
    }
}