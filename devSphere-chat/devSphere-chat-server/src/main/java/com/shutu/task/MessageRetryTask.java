package com.shutu.task;

import com.shutu.common.listener.MessageStreamListener;
import com.shutu.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Redis Stream 消息补偿任务
 * 负责处理 Pending List 中超时未 ACK 的消息（实现重试机制）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRetryTask {

    private final StringRedisTemplate redisTemplate;
    private final MessageStreamListener messageStreamListener; // 复用消息处理逻辑

    /**
     * 每 30 秒执行一次，检查处理超时的消息
     */
    @Scheduled(fixedRate = 30000)
    public void retryPendingMessages() {
        try {
            // 1. 查询 Pending List 中，已读取超过 60 秒还没 ACK 的消息
            // 命令等同于: XPENDING im:message:stream im-group - + 10
            PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                    RedisStreamConfig.IM_STREAM_KEY,
                    Consumer.from(RedisStreamConfig.IM_GROUP, RedisStreamConfig.IM_CONSUMER),
                    Range.unbounded(),
                    10L // 每次只取 10 条，防止积压过多
            );

            if (pendingMessages.isEmpty()) {
                return;
            }

            for (PendingMessage pendingMessage : pendingMessages) {
                // 如果消息空闲时间（IdleTime）小于 60秒，说明还在正常处理中，跳过
                if (pendingMessage.getElapsedTimeSinceLastDelivery().getSeconds() < 60) {
                    continue;
                }

                String recordId = pendingMessage.getIdAsString();
                log.info("发现超时未确认消息，准备重试: id={}, deliveryCount={}", 
                        recordId, pendingMessage.getTotalDeliveryCount());

                // 2. 获取消息详情（XCLAIM 或者 XRANGE）
                // 这里使用 XCLAIM 改变归属（即使是自己抢自己，也能起到重新读取的作用并重置 IdleTime）
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().claim(
                        RedisStreamConfig.IM_STREAM_KEY,
                        RedisStreamConfig.IM_GROUP,
                        RedisStreamConfig.IM_CONSUMER, // 重新归属给自己
                        Duration.ofSeconds(60), // 只有闲置超过60秒的才抢
                        RecordId.of(recordId)
                );

                if (records != null && !records.isEmpty()) {
                    // 3. 再次调用监听器的逻辑进行处理
                    // 注意：这里需要手动转换类型，因为 redisTemplate 泛型差异，实际项目中建议统一泛型
                    MapRecord<String, String, String> record = (MapRecord) records.get(0);
                    messageStreamListener.onMessage(record);
                }
            }
        } catch (Exception e) {
            log.error("重试任务执行异常", e);
        }
    }
}