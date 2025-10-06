package com.shutu.devSphere.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shutu.commons.tools.utils.Result;
import com.shutu.devSphere.model.dto.chat.MessageQueryRequest;
import com.shutu.devSphere.model.dto.chat.RoomQueryRequest;
import com.shutu.devSphere.model.dto.friend.FriendQueryRequest;
import com.shutu.devSphere.model.vo.friend.AddFriendVo;
import com.shutu.devSphere.model.vo.friend.FriendContentVo;
import com.shutu.devSphere.model.vo.room.RoomVo;
import com.shutu.devSphere.model.vo.ws.response.ChatMessageResp;
import com.shutu.devSphere.service.MessageService;
import com.shutu.devSphere.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;


/**
 * 聊天控制器
 */
@RestController
@RequestMapping("/chat")
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final RoomService roomService;
    private final MessageService messageService;


    /**
     * 分页获取用户房间会话列表
     * @param roomQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public Result<Page<RoomVo>> listRoomVoByPage(@RequestBody RoomQueryRequest roomQueryRequest) {
        Page<RoomVo> roomVoPage = roomService.listRoomVoByPage(roomQueryRequest);
        return new Result<Page<RoomVo>>().ok(roomVoPage);
    }


    /**
     * 分页获取用户房间消息列表
     * @param messageQueryRequest
     * @return
     */
    @PostMapping("/message/page/vo")
    public Result<Page<ChatMessageResp>> listMessageVoByPage(@RequestBody MessageQueryRequest messageQueryRequest) {
        Page<ChatMessageResp> messageVoPage = messageService.listMessageVoByPage(messageQueryRequest);
        return new Result<Page<ChatMessageResp>>().ok(messageVoPage);
    }


    /**
     * 获取好友列表
     * @return
     */
    @PostMapping("/friend/list/vo")
    public Result<List<FriendContentVo>> listFriendContentVo() {
        List<FriendContentVo> list = roomService.listFriendContentVo();
        return new Result<List<FriendContentVo>>().ok(list);
    }


    /**
     * 获取群聊或者用户信息
     * @param friendQueryRequest
     * @return
     */
    @PostMapping("/search/friend/vo")
    public Result<AddFriendVo> searchFriendVo(FriendQueryRequest friendQueryRequest) {
        AddFriendVo addFriendVo = roomService.searchFriendVo(friendQueryRequest);
        return new Result<AddFriendVo>().ok(addFriendVo);
    }
}
