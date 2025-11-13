package com.shutu.devSphere.websocket.adapter;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shutu.commons.security.cache.TokenStoreCache;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.utils.Result;
import com.shutu.devSphere.model.dto.ws.GroupMessageDTO;
import com.shutu.devSphere.model.dto.ws.PrivateMessageDTO;
import com.shutu.devSphere.model.entity.RoomFriend;
import com.shutu.devSphere.model.enums.ws.WSReqTypeEnum;
import com.shutu.devSphere.model.vo.ws.response.ChatMessageResp;
import com.shutu.devSphere.model.vo.ws.response.WSBaseResp;
import com.shutu.devSphere.service.RoomFriendService;
import com.shutu.feign.UserFeignClient;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;


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
     * @param privateMessageDTO
     * @return
     */
    public WSBaseResp<ChatMessageResp> buildPrivateMessageResp(PrivateMessageDTO privateMessageDTO) {
        // 获取私信的发送者
        Long loginUserId = privateMessageDTO.getFromUserId();
        //发送信息
        String content = privateMessageDTO.getContent();
        ChatMessageResp chatMessageResp = getMessageVo(loginUserId, content);
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
     * @param groupMessageDTO
     * @return
     */
    public WSBaseResp<ChatMessageResp> buildGroupMessageResp(GroupMessageDTO groupMessageDTO) {
        // 获取私信的发送者
        Long loginUserId = groupMessageDTO.getFromUserId();
        //发送信息
        String content = groupMessageDTO.getContent();
        ChatMessageResp chatMessageResp = getMessageVo(loginUserId, content);
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
     * 构建消息内容及用户信息结构体
     * @param UserId 发送消息的用户id
     * @param content 消息内容
     * @return ChatMessageResp
     */
    @NotNull
    public ChatMessageResp getMessageVo(Long UserId, String content) {
        // 创建ChatMessageResp对象
        ChatMessageResp chatMessageResp = new ChatMessageResp();
        // 获取登录用户的信息
        Result<UserDetail> result = userFeignClient.getById(UserId);
        UserDetail user = result.getData();
        // 创建UserInfo对象
        ChatMessageResp.UserInfo userInfo = new ChatMessageResp.UserInfo();
        // 设置用户名、ID和头像
        userInfo.setUsername(user.getUsername());
        userInfo.setUid(user.getId());
        userInfo.setAvatar(user.getHeadUrl());
        // 和发送者信息
        chatMessageResp.setFromUser(userInfo);
        // 创建Message对象
        ChatMessageResp.Message message = new ChatMessageResp.Message();
        // 设置私信内容
        message.setContent(content);
        // 设置消息对象
        chatMessageResp.setMessage(message);

        return chatMessageResp;
    }


}
