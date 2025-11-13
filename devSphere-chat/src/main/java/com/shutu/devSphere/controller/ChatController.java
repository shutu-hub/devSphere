package com.shutu.devSphere.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.validator.ValidatorUtils;
import com.shutu.commons.tools.validator.group.DefaultGroup;
import com.shutu.devSphere.model.dto.chat.GroupCreateRequestDTO;
import com.shutu.devSphere.model.dto.chat.MessageQueryRequest;
import com.shutu.devSphere.model.dto.chat.RoomQueryRequest;
import com.shutu.devSphere.model.dto.chat.SearchRequestDTO;
import com.shutu.devSphere.model.dto.friend.FriendQueryRequest;
import com.shutu.devSphere.model.vo.friend.AddFriendVo;
import com.shutu.devSphere.model.vo.friend.FriendContentVo;
import com.shutu.devSphere.model.vo.room.RoomVo;
import com.shutu.devSphere.model.vo.ws.response.ChatMessageResp;
import com.shutu.devSphere.service.MessageService;
import com.shutu.devSphere.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
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
     * 创建群聊接口
     * @param dto 包含群名和好友ID列表
     * @return
     */
    @PostMapping("/group/create")
    @Operation(summary = "创建群聊")
    public Result<RoomVo> createGroup(@RequestBody GroupCreateRequestDTO dto) throws Exception {
        // 校验数据
        ValidatorUtils.validateEntity(dto, DefaultGroup.class);

        // 调用服务层创建群聊
        RoomVo newRoom = roomService.createGroup(dto);
        return new Result<RoomVo>().ok(newRoom);
    }

    /**
     * 搜索可添加的好友(按用户名)或群聊(按ID)
     * @param searchRequestDTO
     * @return
     */
    @PostMapping("/search/addable")
    public Result<AddFriendVo> searchForAdd(@RequestBody SearchRequestDTO searchRequestDTO) {
        AddFriendVo addFriendVo = roomService.searchForAdd(searchRequestDTO.getQuery());
        return new Result<AddFriendVo>().ok(addFriendVo);
    }
}
