package com.shutu.devSphere.service.impl;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.devSphere.mapper.MessageMapper;
import com.shutu.devSphere.mapper.RoomMapper;
import com.shutu.devSphere.model.dto.chat.CursorPage;
import com.shutu.devSphere.model.dto.chat.MessageQueryRequest;
import com.shutu.devSphere.model.entity.Message;
import com.shutu.devSphere.model.entity.Room;
import com.shutu.devSphere.model.entity.UserRoomRelate;
import com.shutu.devSphere.model.vo.ws.response.ChatMessageResp;
import com.shutu.devSphere.service.MessageService;
import com.shutu.devSphere.service.RoomService;
import com.shutu.devSphere.service.UserRoomRelateService;
import com.shutu.devSphere.websocket.adapter.WSAdapter;
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
        if (!update){
            throw new CommonException("更新已读消息失败", ErrorCode.INTERNAL_SERVER_ERROR);
        }

        UserRoomRelate relate = userRoomRelateService.getOne(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, loginUserId));
        Long minMsgId = (relate != null && relate.getMinMsgId() != null) ? relate.getMinMsgId() : 0L;

        int fetchSize = size + 1;
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getRoomId, roomId)
                .gt(Message::getId, minMsgId); //过滤掉删除前的历史消息

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

        List<ChatMessageResp> chatMessageRespList = messageList.stream()
                .map(wsAdapter::getMessageVo)
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
        if (!update){
            throw new CommonException("更新已读消息失败", ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}




