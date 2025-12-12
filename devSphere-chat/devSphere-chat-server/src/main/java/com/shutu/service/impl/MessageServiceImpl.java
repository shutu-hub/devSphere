package com.shutu.service.impl;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.mapper.MessageMapper;
import com.shutu.model.dto.chat.CursorPage;
import com.shutu.model.dto.chat.MessageQueryRequest;
import com.shutu.model.entity.Message;
import com.shutu.model.entity.Room;
import com.shutu.model.entity.UserRoomRelate;
import com.shutu.model.vo.ws.response.ChatMessageResp;
import com.shutu.service.MessageService;
import com.shutu.service.UserRoomRelateService;
import com.shutu.websocket.adapter.WSAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
        implements MessageService {

    private final WSAdapter wsAdapter;
    private final UserRoomRelateService userRoomRelateService;

    @Override
    public CursorPage<ChatMessageResp> listMessageVoByPage(MessageQueryRequest messageQueryRequest) {
        Long roomId = messageQueryRequest.getRoomId();
        int size = messageQueryRequest.getPageSize() != null ? messageQueryRequest.getPageSize() : 20;
        String cursor = messageQueryRequest.getCursor();

        if (roomId == null) {
            CursorPage<ChatMessageResp> emptyPage = new CursorPage<>();
            emptyPage.setRecords(List.of());
            emptyPage.setNextCursor(null);
            emptyPage.setHasMore(false);
            return emptyPage;
        }

        // 获取当前登录用户ID
        Long loginUserId = SecurityUser.getUserId();

        // 查找此房间的最新一条消息
        Room room = Db.lambdaQuery(Room.class).select(Room::getLastMsgId)
                .eq(Room::getId, roomId).one();
        Long latestMessageId = (room != null) ? room.getLastMsgId() : null;
        // 更新用户在该房间的最新已读消息
        boolean update = userRoomRelateService.lambdaUpdate().eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, loginUserId)
                .set(UserRoomRelate::getLatestReadMsgId, latestMessageId)
                .update();
        if (!update) {
            // 忽略更新失败，不影响主流程
            // log.warn("更新已读消息失败: roomId={}, userId={}", roomId, loginUserId);
        }

        UserRoomRelate relate = userRoomRelateService.getOne(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, loginUserId));
        Long minMsgId = (relate != null && relate.getMinMsgId() != null) ? relate.getMinMsgId() : 0L;

        int fetchSize = size + 1;
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getRoomId, roomId)
                .gt(Message::getId, minMsgId); // 过滤掉删除前的历史消息

        // 如果 cursor 不为 null，则查询 ID < cursor 的消息 (更早的消息)
        if (cursor != null) {
            try {
                wrapper.lt(Message::getId, Long.parseLong(cursor));
            } catch (NumberFormatException e) {
                // 如果游标格式错误，返回空
                CursorPage<ChatMessageResp> emptyPage = new CursorPage<>();
                emptyPage.setRecords(List.of());
                emptyPage.setNextCursor(null);
                emptyPage.setHasMore(false);
                return emptyPage;
            }
        }
        // 核心查询：按ID倒序（最新->最旧），限制 N+1 条
        wrapper.orderByDesc(Message::getId);
        wrapper.last("LIMIT " + fetchSize);

        List<Message> messageList = this.list(wrapper);

        // 判断是否还有更多
        boolean hasMore = messageList.size() > size;
        if (hasMore) {
            // 移除多查的那一条，它仅用于判断
            messageList.remove(messageList.size() - 1);
        }

        // 批量获取用户信息 (优化 N+1 问题)
        java.util.Set<Long> userIds = messageList.stream()
                .map(Message::getFromUid)
                .collect(Collectors.toSet());

        java.util.Map<Long, com.shutu.commons.security.user.UserDetail> userDetailMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                // 调用 Feign 批量接口
                com.shutu.commons.tools.utils.Result<List<com.shutu.dto.SysUserDTO>> userResult = com.shutu.commons.tools.utils.SpringContextUtils
                        .getBean(com.shutu.feign.UserFeignClient.class)
                        .listByIds(new java.util.ArrayList<>(userIds));

                if (userResult.getData() != null) {
                    for (com.shutu.dto.SysUserDTO dto : userResult.getData()) {
                        // 将 DTO 转换为 UserDetail (简单映射，仅需头像和昵称)
                        com.shutu.commons.security.user.UserDetail userDetail = new com.shutu.commons.security.user.UserDetail();
                        userDetail.setId(dto.getId());
                        userDetail.setUsername(dto.getUsername()); // 注意：这里可能需要用 RealName 或 Nickname，视业务而定，暂用 Username
                        userDetail.setHeadUrl(dto.getHeadUrl());
                        userDetailMap.put(dto.getId(), userDetail);
                    }
                }
            } catch (Exception e) {
                log.error("批量获取用户信息失败", e);
            }
        }

        List<ChatMessageResp> chatMessageRespList = messageList.stream()
                .map(msg -> wsAdapter.buildBatchMessageResp(msg, userDetailMap))
                .collect(Collectors.toList());

        // 反转列表，使之符合前端渲染顺序 (最旧 -> 最新)
        List<ChatMessageResp> finalRecords = ListUtil.reverse(chatMessageRespList);

        // 计算下一次查询的游标 (即本次查询结果中最旧的那条消息的ID)
        String nextCursor = null;
        if (!messageList.isEmpty()) {
            // 注意：这里用 messageList (反转前)，获取最后一条 (最旧的)
            nextCursor = String.valueOf(messageList.get(messageList.size() - 1).getId());
        }

        // 组装返回
        CursorPage<ChatMessageResp> cursorPage = new CursorPage<>();
        cursorPage.setRecords(finalRecords);
        cursorPage.setNextCursor(hasMore ? nextCursor : null); // 如果没有更多了，游标设为null
        cursorPage.setHasMore(hasMore);

        return cursorPage;
    }

    /**
     * 将用户在某个会话中的消息标记为已读
     */
    @Override
    @Transactional
    public void markConversationAsRead(Long roomId) {
        // 获取当前登录用户ID
        Long loginUserId = SecurityUser.getUserId();
        // 查找此房间的最新一条消息
        Room room = Db.lambdaQuery(Room.class).select(Room::getLastMsgId)
                .eq(Room::getId, roomId).one();
        Long latestMessageId = (room != null) ? room.getLastMsgId() : null;
        // 更新用户在该房间的最新已读消息
        boolean update = userRoomRelateService.lambdaUpdate().eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, loginUserId)
                .set(UserRoomRelate::getLatestReadMsgId, latestMessageId)
                .update();
        if (!update) {
            throw new CommonException("更新已读消息失败", ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<ChatMessageResp> searchHistory(Long roomId, String keyword) {
        Long userId = SecurityUser.getUserId();

        // 1. 获取 minMsgId
        UserRoomRelate relate = userRoomRelateService.getOne(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, userId));
        Long minMsgId = (relate != null && relate.getMinMsgId() != null) ? relate.getMinMsgId() : 0L;

        // 2. 查询消息
        List<Message> list = this.list(new LambdaQueryWrapper<Message>()
                .eq(Message::getRoomId, roomId)
                .gt(Message::getId, minMsgId)
                .like(Message::getContent, keyword)
                .orderByDesc(Message::getId));

        // 3. 转换 VO (复用逻辑)
        if (list.isEmpty()) {
            return List.of();
        }

        java.util.Set<Long> userIds = list.stream().map(Message::getFromUid).collect(Collectors.toSet());
        java.util.Map<Long, com.shutu.commons.security.user.UserDetail> userDetailMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                com.shutu.commons.tools.utils.Result<List<com.shutu.dto.SysUserDTO>> userResult = com.shutu.commons.tools.utils.SpringContextUtils
                        .getBean(com.shutu.feign.UserFeignClient.class)
                        .listByIds(new java.util.ArrayList<>(userIds));
                if (userResult.getData() != null) {
                    for (com.shutu.dto.SysUserDTO dto : userResult.getData()) {
                        com.shutu.commons.security.user.UserDetail userDetail = new com.shutu.commons.security.user.UserDetail();
                        userDetail.setId(dto.getId());
                        userDetail.setUsername(dto.getUsername());
                        userDetail.setHeadUrl(dto.getHeadUrl());
                        userDetailMap.put(dto.getId(), userDetail);
                    }
                }
            } catch (Exception e) {
                log.error("批量获取用户信息失败", e);
            }
        }

        return list.stream()
                .map(msg -> wsAdapter.buildBatchMessageResp(msg, userDetailMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recallMessage(Long messageId) {
        Long userId = SecurityUser.getUserId();
        Message message = this.getById(messageId);
        if (message == null) {
            throw new CommonException("消息不存在", ErrorCode.DATA_NOT_EXIST);
        }

        if (!message.getFromUid().equals(userId)) {
            throw new CommonException("只能撤回自己的消息", ErrorCode.FORBIDDEN);
        }

        // 检查时间 (2分钟内)
        long diff = System.currentTimeMillis() - message.getCreateTime().getTime();
        if (diff > 2 * 60 * 1000) {
            throw new CommonException("超过2分钟的消息无法撤回", ErrorCode.FORBIDDEN);
        }

        // 修改消息类型为撤回
        message.setType(com.shutu.model.enums.chat.MessageTypeEnum.RECALL.getType());
        message.setContent("撤回了一条消息");
        this.updateById(message);
    }
}
