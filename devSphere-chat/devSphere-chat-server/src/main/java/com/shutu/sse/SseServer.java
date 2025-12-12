package com.shutu.sse;

import cn.hutool.core.map.MapUtil;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * SseServer业务封装类来操作SEE
 */
@Slf4j
public class SseServer {
    private SseServer() {

    }

    /**
     * 当前连接总数
     */
    private static final AtomicInteger CURRENT_CONNECT_TOTAL = new AtomicInteger(0);

    /**
     * [FIXED] userId的 SseEmitter对象映射集
     */
    private static final Map<String, SseEmitter> SSE_EMITTER_MAP = new ConcurrentHashMap<>();


    /**
     * 创建sse连接
     *
     * @param userId - 用户id（唯一）
     * @return {@link SseEmitter}
     */
    public static SseEmitter createConnect(String userId) {
        //设置连接超时时间。0表示不过期，默认是30秒，超过时间未完成会抛出异常
        SseEmitter sseEmitter = new SseEmitter(0L);
        try {
            sseEmitter.send(
                    SseEmitter.event()
                            .reconnectTime(1000L)
                    //.data("前端重连成功") // 重连成功的提示信息
            );
        } catch (IOException e) {
            log.error("SSE 前端重连异常 ==> userId={}, 异常信息：{}", userId, e.getMessage());
        }
        // 注册回调
        sseEmitter.onCompletion(completionCallBack(userId));
        sseEmitter.onTimeout(timeOutCallBack(userId));
        sseEmitter.onError(errorCallBack(userId));
        SSE_EMITTER_MAP.put(userId, sseEmitter);

        //记录一下连接总数。数量+1
        int count = CURRENT_CONNECT_TOTAL.incrementAndGet();
        log.info("创建SSE连接成功 ==> 当前连接总数={}， userId={}", count, userId);
        return sseEmitter;
    }


    public static boolean containUser(String userId) {
        return SSE_EMITTER_MAP.containsKey(userId);
    }

    /**
     * 给指定 userId 发消息
     *
     * @param userId - 用户id（唯一）
     * @param message   - 消息文本
     */
    public static void sendMessage(String userId, String message) {
        if (SSE_EMITTER_MAP.containsKey(userId)) {
            try {
                SSE_EMITTER_MAP.get(userId).send(message);
            } catch (IOException e) {
                log.error("SSE 发送消息异常 ==> userId={}, 异常信息：{}", userId, e.getMessage());
            }
        } else {
            log.warn("SSE 发送消息失败，连接不存在或者超时， userId={}", userId);
            // throw new CommonException("连接不存在或者超时， userId=" + userId, ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 批量全部发送消息
     * 给所有 userId 广播发送消息
     *
     * @param message 消息
     */
    public static void batchAllSendMessage(String message) {
        SSE_EMITTER_MAP.forEach((userId, sseEmitter) -> {
            try {
                sseEmitter.send(message, MediaType.APPLICATION_JSON);
            } catch (IOException e) {
                log.error("SSE 广播发送消息异常 ==> userId={}, 异常信息：{}", userId, e.getMessage());
                removeUserId(userId);
            }
        });
    }

    /**
     * 批量发送消息
     * 给指定 userId 集合群发消息
     *
     * @param userIds 用户 ID 列表
     * @param message    消息
     */
    public static void batchSendMessage(List<String> userIds, String message) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        // 去重
        userIds = userIds.stream().distinct().collect(Collectors.toList());
        userIds.forEach(userId -> sendMessage(userId, message));
    }


    /**
     * 群发消息
     * 给指定组群发消息（即组播，我们让 userId 满足我们的组命名确定即可）
     *
     * @param groupId 组 ID
     * @param message 消息
     */
    public static void groupSendMessage(String groupId, String message) {
        if (MapUtil.isEmpty(SSE_EMITTER_MAP)) {
            return;
        }
        SSE_EMITTER_MAP.forEach((userId, sseEmitter) -> {
            try {
                // 这里 groupId 作为前缀
                if (userId.startsWith(groupId)) {
                    sseEmitter.send(message, MediaType.APPLICATION_JSON);
                }
            } catch (IOException e) {
                log.error("SSE 组播发送消息异常 ==> groupId={}, 异常信息：{}", groupId, e.getMessage());
                removeUserId(userId);
            }
        });
    }

    /**
     * [FIXED] 移除 UserId
     *
     * @param userId 用户 ID
     */
    public static void removeUserId(String userId) {
        SSE_EMITTER_MAP.remove(userId);
        //数量-1
        CURRENT_CONNECT_TOTAL.getAndDecrement();
        log.info("SSE remove userId={}", userId);
    }

    /**
     * [FIXED] 获取所有的 UserId 集合
     *
     * @return {@link List}<{@link String}>
     */
    public static List<String> getUserIds() {
        return new ArrayList<>(SSE_EMITTER_MAP.keySet());
    }

    /**
     * 获取当前连接总数
     *
     * @return int
     */
    public static int getConnectTotal() {
        return CURRENT_CONNECT_TOTAL.intValue();
    }

    /**
     * 完成回拨
     * 断开SSE连接时的回调
     *
     * @param userId 用户 ID
     * @return {@link Runnable}
     */
    private static Runnable completionCallBack(String userId) {
        return () -> {
            log.info("SSE 结束连接 ==> userId={}", userId);
            removeUserId(userId);
        };
    }

    /**
     * 超时回拨
     * 连接超时时回调触发
     *
     * @param userId 用户 ID
     * @return {@link Runnable}
     */
    private static Runnable timeOutCallBack(String userId) {
        return () -> {
            log.info("SSE 连接超时 ==> userId={}", userId);
            removeUserId(userId);
        };
    }

    /**
     * 错误回调
     * 连接报错时回调触发。
     *
     * @param userId 用户 ID
     * @return {@link Consumer}<{@link Throwable}>
     */
    private static Consumer<Throwable> errorCallBack(String userId) {
        return throwable -> {
            log.error("SSE 连接异常 ==> userId={}", userId);
            removeUserId(userId);
        };
    }
}