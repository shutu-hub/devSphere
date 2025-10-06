package com.shutu.devSphere.common.event;

import com.shutu.devSphere.model.dto.ws.PrivateMessageDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 用户私聊事件
 */
@Getter
public class PrivateMessageEvent extends ApplicationEvent {

    private final PrivateMessageDTO privateMessageDTO;


    public PrivateMessageEvent(Object source, PrivateMessageDTO privateMessageDTO) {
        super(source);
        this.privateMessageDTO = privateMessageDTO;
    }
}
