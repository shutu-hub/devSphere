package com.shutu.config;

import com.shutu.common.listener.RouteMessageListener;
import com.shutu.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;


/**
 * Redis 发布订阅配置类
 * 负责配置 Redis 消息监听容器，绑定路由消息监听器到专属的 Topic
 */
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    private final NodeConfig nodeConfig;
    private final RouteMessageListener routeMessageListener;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 监听专属 Topic: im:route:to:{myNodeId}
        String topic = RedisKeyConstant.TOPIC_NODE_ROUTE_PREFIX + nodeConfig.getNodeId();

        // 使用 MessageListenerAdapter 也可以，或者直接用实现类
        container.addMessageListener(routeMessageListener, new PatternTopic(topic));

        System.out.println(">>> [Redis PubSub] Listening on topic: " + topic);
        return container;
    }
}
