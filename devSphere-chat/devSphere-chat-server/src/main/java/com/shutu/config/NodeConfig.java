package com.shutu.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * 节点配置与常量定义类
 * 负责生成当前服务节点的唯一标识，用于分布式路由场景下的节点识别
 */
@Data
@Slf4j
@Component
public class NodeConfig {

    @Value("${devsphere.server.node-id}")
    private String nodeId;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 获取动态消费者名称：应用名:端口号
     * 确保每个节点都是独立消费者
     */
    public String getConsumerName() {
        return nodeId + ":" + serverPort;
    }

    /**
     * 初始化该服务节点的标识
     */
    @PostConstruct
    public void init() {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Node ID 必须在配置文件中指定：devsphere.server.node-id");
        }
        log.info(">>> [IM Node Init] 当前 IM 节点 ID: " + this.nodeId);
    }
}
