package com.shutu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.utils.SpringContextUtils;
import com.shutu.dto.SysUserDTO;
import com.shutu.feign.UserFeignClient;
import com.shutu.mapper.MessageMapper;
import com.shutu.model.dto.chat.CursorPage;
import com.shutu.model.dto.chat.MessageQueryRequest;
import com.shutu.model.entity.Message;
import com.shutu.model.entity.MessageArchive;
import com.shutu.model.entity.Room;
import com.shutu.model.entity.UserRoomRelate;
import com.shutu.model.vo.ws.response.ChatMessageResp;
import com.shutu.service.MessageArchiveService;
import com.shutu.service.MessageService;
import com.shutu.service.UserRoomRelateService;
import com.shutu.websocket.adapter.WSAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.shutu.config.NodeConfig;
import com.shutu.constant.RedisKeyConstant;
import cn.hutool.json.JSONUtil;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
        implements MessageService {

    private final WSAdapter wsAdapter;
    private final UserRoomRelateService userRoomRelateService;
    private final StringRedisTemplate redisTemplate;
    private final MessageArchiveService messageArchiveService;

    @Override
    public CursorPage<ChatMessageResp> listMessageVoByPage(MessageQueryRequest messageQueryRequest) {
        Long roomId = messageQueryRequest.getRoomId();
        int size = messageQueryRequest.getPageSize() != null ? messageQueryRequest.getPageSize() : 20;
        String cursor = messageQueryRequest.getCursor();

        if (roomId == null) {
            CursorPage<ChatMessageResp> emptyPage = new CursorPage<>();
            emptyPage.setRecords(java.util.Collections.emptyList());
            emptyPage.setNextCursor(null);
            emptyPage.setHasMore(false);
            return emptyPage;
        }

        // 1. 获取当前用户及最小可见消息ID
        Long loginUserId = SecurityUser.getUserId();
        Long minMsgId = getMinMsgId(roomId, loginUserId);

        // 2. 异步更新已读状态
        updateReadStatusAsync(roomId, loginUserId);

        // 获取房间最新消息ID，用于缓存新鲜度校验,避免缓存和数据库的不一致性
        Room room = Db.lambdaQuery(Room.class).select(Room::getLastMsgId)
                .eq(Room::getId, roomId).one();
        Long latestMessageId = (room != null) ? room.getLastMsgId() : null;

        List<Message> messageList = null;
        int fetchSize = size + 1;

        // 3. 尝试从 Redis 缓存读取 (仅限首页查询)
        if (cursor == null) {
            messageList = tryGetFromCache(roomId, size, minMsgId, latestMessageId);
        }

        // 4. 如果缓存未命中或不足，降级查 DB
        if (messageList == null) {
            messageList = queryFromDb(roomId, minMsgId, cursor, fetchSize);

            // 5. 触发 Read-Repair (仅限首页查询且有数据时)
            if (cursor == null && !messageList.isEmpty()) {
                rebuildCacheAsync(roomId, messageList);
            }
        }

        // 6. 组装分页结果
        boolean hasMore = messageList.size() > size;
        if (hasMore) {
            messageList.remove(messageList.size() - 1);
        }

        // 7. 批量转换 DTO
        List<ChatMessageResp> chatMessageRespList = convertMessagesToVos(messageList);
        List<ChatMessageResp> finalRecords = ListUtil.reverse(chatMessageRespList);

        String nextCursor = getNextCursor(messageList);

        CursorPage<ChatMessageResp> cursorPage = new CursorPage<>();
        cursorPage.setRecords(finalRecords);
        cursorPage.setNextCursor(hasMore ? nextCursor : null);
        cursorPage.setHasMore(hasMore);

        return cursorPage;
    }

    /**
     * 将用户在某个会话中的消息标记为已读
     */
    @Override
    @Transactional
    public void markConversationAsRead(Long roomId) {
        Long loginUserId = SecurityUser.getUserId();
        updateReadStatusAsync(roomId, loginUserId);
    }

    @Override
    public List<ChatMessageResp> searchHistory(Long roomId, String keyword) {
        Long userId = SecurityUser.getUserId();
        Long minMsgId = getMinMsgId(roomId, userId);

        List<Message> list = this.list(new LambdaQueryWrapper<Message>()
                .eq(Message::getRoomId, roomId)
                .gt(Message::getId, minMsgId)
                .like(Message::getContent, keyword)
                .orderByDesc(Message::getId));

        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        return convertMessagesToVos(list);
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

    /**
     * 获取用户在这个房间内最早可见的消息ID (用于处理由"清除历史消息"产生的边界)
     */
    private Long getMinMsgId(Long roomId, Long userId) {
        UserRoomRelate relate = userRoomRelateService.getOne(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, userId));
        return (relate != null && relate.getMinMsgId() != null) ? relate.getMinMsgId() : 0L;
    }

    /**
     * 更新用户已读状态
     */
    @Async
    protected void updateReadStatusAsync(Long roomId, Long userId) {
        try {
            Room room = Db.lambdaQuery(Room.class).select(Room::getLastMsgId)
                    .eq(Room::getId, roomId).one();
            Long latestMessageId = (room != null) ? room.getLastMsgId() : null;
            if (latestMessageId != null) {
                userRoomRelateService.lambdaUpdate()
                        .eq(UserRoomRelate::getRoomId, roomId)
                        .eq(UserRoomRelate::getUserId, userId)
                        .set(UserRoomRelate::getLatestReadMsgId, latestMessageId)
                        .update();
            }
        } catch (Exception e) {
            log.warn("[已读状态] 更新失败: 房间ID={}, 用户ID={}, 错误={}", roomId, userId, e.getMessage());
        }
    }

    /**
     * 尝试从 Redis ZSet 读取缓存
     * 
     * @return 如果命中且完整，返回 List；否则返回 null
     */
    private List<Message> tryGetFromCache(Long roomId, int size, Long minMsgId, Long latestDbMsgId) {
        try {
            String cacheKey = RedisKeyConstant.IM_ROOM_MSG_KEY + roomId;
            // 倒序取最新的 N+1 条
            Set<String> jsonSet = redisTemplate.opsForZSet().reverseRange(cacheKey, 0, size);

            if (jsonSet == null || jsonSet.size() < (size + 1)) {
                // 缓存条数不足，说明缓存不可靠，强制走DB
                log.info("[Redis缓存] 命中但数据不完整: 房间ID={}, 期望条数={}, 实际条数={}", roomId, size + 1,
                        (jsonSet == null ? 0 : jsonSet.size()));
                return null;
            }

            List<Message> cachedMessages = jsonSet.stream()
                    .map(json -> JSONUtil.toBean(json, Message.class))
                    .filter(msg -> msg.getId() > minMsgId)
                    .sorted((a, b) -> Long.compare(b.getId(), a.getId())) // 倒序
                    .collect(Collectors.toList());

            if (cachedMessages.isEmpty()) {
                return null;
            }

            // 如果 DB 里的最新消息 ID 比 缓存里最新的消息 ID 还要大，说明缓存漏了最新的消息
            if (latestDbMsgId != null) {
                Long latestCacheId = cachedMessages.get(0).getId();
                if (latestDbMsgId > latestCacheId) {
                    log.warn("[Redis缓存] 数据陈旧 (Stale Cache): 房间ID={}, DB最新ID={}, 缓存最新ID={}",
                            roomId, latestDbMsgId, latestCacheId);
                    return null; // 降级查 DB
                }
            }

            log.debug("[Redis缓存] 命中成功: 房间ID={}, 条数={}", roomId, cachedMessages.size());
            return cachedMessages;

        } catch (Exception e) {
            log.error("[Redis缓存] 读取异常: 房间ID={}", roomId, e);
            return null; // 异常降级
        }
    }


    /**
     * 从数据库查询消息 (支持冷热分离路由)
     * 策略：优先查主表，数据不足时自动“穿透”到归档表补齐
     */
    private List<Message> queryFromDb(Long roomId, Long minMsgId, String cursor, int fetchSize) {
        // 1. 先查主表 (热数据/温数据)
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getRoomId, roomId)
                .gt(Message::getId, minMsgId);

        if (cursor != null) {
            try {
                wrapper.lt(Message::getId, Long.parseLong(cursor));
            } catch (NumberFormatException e) {
                log.warn("[消息查询] 游标无效: {}", cursor);
                return Collections.emptyList();
            }
        }

        wrapper.orderByDesc(Message::getId);
        wrapper.last("LIMIT " + fetchSize);
        List<Message> mainList = this.list(wrapper);

        // 2. 如果主表数据不够 (说明可能碰到了归档边界)，继续查归档表 (冷数据)
        if (mainList.size() < fetchSize) {
            int needed = fetchSize - mainList.size();
            // 计算归档表游标: 衔接主表最后一条数据的 ID
            String archiveCursor = cursor;
            if (!mainList.isEmpty()) {
                archiveCursor = String.valueOf(mainList.get(mainList.size() - 1).getId());
            }

            // 穿透查询归档表
            List<Message> archiveList = queryFromArchive(roomId, minMsgId, archiveCursor, needed);
            if (!archiveList.isEmpty()) {
                mainList.addAll(archiveList);
            }
        }

        return mainList;
    }

    /**
     * 查询归档表 (冷数据)
     */
    private List<Message> queryFromArchive(Long roomId, Long minMsgId, String cursor, int limit) {
        try {
            LambdaQueryWrapper<MessageArchive> wrapper = new LambdaQueryWrapper<MessageArchive>()
                    .eq(MessageArchive::getRoomId, roomId)
                    .gt(MessageArchive::getId, minMsgId);

            if (cursor != null) {
                wrapper.lt(MessageArchive::getId, Long.parseLong(cursor));
            }

            wrapper.orderByDesc(com.shutu.model.entity.MessageArchive::getId);
            wrapper.last("LIMIT " + limit);

            List<MessageArchive> archives = messageArchiveService.list(wrapper);
            if (archives.isEmpty()) {
                return Collections.emptyList();
            }

            log.info("[归档查询] 命中冷数据: 房间ID={}, 条数={}", roomId, archives.size());

            // 实体转换: MessageArchive -> Message
            return archives.stream().map(archive -> {
                Message msg = new Message();
                BeanUtil.copyProperties(archive, msg);
                return msg;
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[归档查询] 异常: 房间ID={}", roomId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 异步回填缓存 (Read-Repair)
     */
    private void rebuildCacheAsync(Long roomId, List<Message> messages) {
        // 这里可以直接同步执行，或者丢到线程池。目前量小先同步。
        try {
            String cacheKey = RedisKeyConstant.IM_ROOM_MSG_KEY + roomId;
            for (Message msg : messages) {
                String json = JSONUtil.toJsonStr(msg);
                redisTemplate.opsForZSet().add(cacheKey, json, msg.getId());
            }
            redisTemplate.expire(cacheKey, java.time.Duration.ofDays(7));
            log.info("[Redis缓存] 缓存已回填: 房间ID={}, 条数={}", roomId, messages.size());
        } catch (Exception e) {
            log.error("[Redis缓存] 回填失败: 房间ID={}", roomId, e);
        }
    }

    /**
     * 批量获取用户信息并转换为 VO
     */
    private List<ChatMessageResp> convertMessagesToVos(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = messages.stream()
                .map(Message::getFromUid)
                .collect(Collectors.toSet());

        Map<Long, UserDetail> userDetailMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                // 远程调用用户服务
                Result<List<SysUserDTO>> userResult = SpringContextUtils
                        .getBean(UserFeignClient.class)
                        .listByIds(new ArrayList<>(userIds));

                if (userResult.getData() != null) {
                    for (SysUserDTO dto : userResult.getData()) {
                        UserDetail userDetail = new UserDetail();
                        userDetail.setId(dto.getId());
                        userDetail.setUsername(dto.getUsername());
                        userDetail.setHeadUrl(dto.getHeadUrl());
                        userDetailMap.put(dto.getId(), userDetail);
                    }
                }
            } catch (Exception e) {
                log.error("[用户服务] 批量获取用户信息失败", e);
            }
        }

        return messages.stream()
                .map(msg -> wsAdapter.buildBatchMessageResp(msg, userDetailMap))
                .collect(Collectors.toList());
    }

    private String getNextCursor(List<Message> messageList) {
        if (messageList != null && !messageList.isEmpty()) {
            return String.valueOf(messageList.get(messageList.size() - 1).getId());
        }
        return null;
    }
}
