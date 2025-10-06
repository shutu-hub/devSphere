package com.shutu.devSphere.common.event;

import com.shutu.devSphere.model.dto.ws.GroupMessageDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GroupMessageEvent extends ApplicationEvent {

    private final GroupMessageDTO groupMessageDTO;


    public GroupMessageEvent(Object source, GroupMessageDTO groupMessageDTO) {
        super(source);
        this.groupMessageDTO = groupMessageDTO;
    }
}
