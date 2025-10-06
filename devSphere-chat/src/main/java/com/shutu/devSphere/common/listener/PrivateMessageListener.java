package com.shutu.devSphere.common.listener;

import com.shutu.devSphere.common.event.PrivateMessageEvent;
import com.shutu.devSphere.model.dto.ws.PrivateMessageDTO;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public class PrivateMessageListener {

    /**
     * 监听私聊消息，保存至数据库
     * @param event
     */
    @Async
    @EventListener(classes = PrivateMessageEvent.class)
    public void handlePrivateMessage(PrivateMessageEvent event) {
        PrivateMessageDTO privateMessageDTO = event.getPrivateMessageDTO();
    }
}
