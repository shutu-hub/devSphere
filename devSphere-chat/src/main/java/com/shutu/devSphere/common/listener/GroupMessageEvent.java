package com.shutu.devSphere.common.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public class GroupMessageEvent {

    /**
     * 监听群聊消息，保存至数据库
     */
    @Async
    @EventListener(classes = GroupMessageEvent.class)
    public void handleGroupMessage() {

    }




}
