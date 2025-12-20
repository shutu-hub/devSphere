package com.shutu.constant;

/**
 * Redis Key 常量定义
 */
public interface RedisKeyConstant {

    /**
     * 用户位置 Key (Geo)
     * 后缀: uid
     */
    String KEY_USER_LOCATION = "im:location:";

    /**
     * 节点路由 Topic 前缀
     * 后缀: nodeId
     */
    String TOPIC_NODE_ROUTE_PREFIX = "im:route:to:";

    /**
     * 房间消息缓存 Key (ZSet)
     * 后缀: roomId
     */
    String IM_ROOM_MSG_KEY = "im:room:msg:";
}
