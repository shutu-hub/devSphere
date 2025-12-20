package com.shutu.service.impl;

import com.shutu.config.NodeConfig;
import com.shutu.constant.RedisKeyConstant;
import com.shutu.service.UserLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 用户位置服务实现类
 * 负责在 Redis 中维护 UserID 到 NodeID 的映射关系
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLocationServiceImpl implements UserLocationService {

    private final StringRedisTemplate redisTemplate;
    private final NodeConfig nodeConfig;

    private DefaultRedisScript<Long> removeScript;

    // 位置信息过期时间 (秒)，略大于心跳间隔
    private static final long LOCATION_EXPIRE = 60;

    @PostConstruct
    public void init() {
        removeScript = new DefaultRedisScript<>();
        removeScript.setResultType(Long.class);
        removeScript
                .setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/remove_user_location.lua")));
    }

    /**
     * 注册用户位置（上线/心跳）
     */
    @Override
    public void register(Long uid) {
        String key = RedisKeyConstant.KEY_USER_LOCATION + uid;
        // 绑定当前节点 ID
        redisTemplate.opsForValue().set(key, nodeConfig.getNodeId(), LOCATION_EXPIRE, TimeUnit.SECONDS);
    }

    /**
     * 移除用户位置（下线）
     */
    @Override
    public void remove(Long uid) {
        String key = RedisKeyConstant.KEY_USER_LOCATION + uid;
        String currentNodeId = nodeConfig.getNodeId();

        // 使用 Lua 脚本保证原子性
        redisTemplate.execute(removeScript, Collections.singletonList(key), currentNodeId);
    }

    /**
     * 获取用户所在的节点 ID
     */
    @Override
    public String getNode(Long uid) {
        String key = RedisKeyConstant.KEY_USER_LOCATION + uid;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 判断是否是本机
     */
    @Override
    public boolean isLocal(String targetNodeId) {
        return nodeConfig.getNodeId().equals(targetNodeId);
    }
}
