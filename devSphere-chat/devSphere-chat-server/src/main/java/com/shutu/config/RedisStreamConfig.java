package com.shutu.config;

import com.shutu.common.listener.DlqMessageListener; // [NEW] 引入 DLQ 监听器
import com.shutu.common.listener.MessageStreamListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final MessageStreamListener messageStreamListener;
    private final DlqMessageListener dlqMessageListener;
    private final StringRedisTemplate redisTemplate;

    // 主业务定义
    public static final String IM_STREAM_KEY = "im:message:stream";
    public static final String IM_GROUP = "im-group";
    public static final String IM_CONSUMER = "im-consumer-1";

    // 死信队列定义
    public static final String DLQ_STREAM_KEY = "im:message:dlq";
    public static final String DLQ_GROUP = "dlq-group";
    public static final String DLQ_CONSUMER = "dlq-consumer-1";

    @Bean
    public Subscription subscription(RedisConnectionFactory factory) {
        // 1. 初始化消费者组 (主业务 + 死信)
        createGroup(IM_STREAM_KEY, IM_GROUP);
        createGroup(DLQ_STREAM_KEY, DLQ_GROUP);

        // 2. 配置监听容器选项
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1)) // 轮询超时
                        .batchSize(10) // 每次拉取条数
                        .build();

        // 3. 创建容器
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        // 4. 注册监听器
        // 4.1 主业务监听器 (处理正常消息)
        container.receive(
                Consumer.from(IM_GROUP, IM_CONSUMER),
                StreamOffset.create(IM_STREAM_KEY, ReadOffset.lastConsumed()),
                messageStreamListener
        );

        // 4.2 死信队列监听器 (处理毒消息)
        // DLQ 的逻辑比较简单（只入库），也可以复用 container
        Subscription subscription = container.receive(
                Consumer.from(DLQ_GROUP, DLQ_CONSUMER),
                StreamOffset.create(DLQ_STREAM_KEY, ReadOffset.lastConsumed()),
                dlqMessageListener
        );

        // 5. 启动容器
        container.start();
        return subscription;
    }


    /**
     * 安全创建消费者组
     * 避免重复 try-catch 代码
     */
    private void createGroup(String key, String group) {
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
                redisTemplate.opsForStream().createGroup(key, group);
            } else {
                redisTemplate.opsForStream().createGroup(key, group);
            }
        } catch (Exception e) {
            // Group 已存在是正常现象，忽略
        }
    }
}