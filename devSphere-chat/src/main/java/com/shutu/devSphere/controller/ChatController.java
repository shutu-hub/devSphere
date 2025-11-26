package com.shutu.devSphere.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.validator.ValidatorUtils;
import com.shutu.commons.tools.validator.group.DefaultGroup;
import com.shutu.devSphere.model.dto.chat.*;
import com.shutu.devSphere.model.vo.friend.AddFriendVo;
import com.shutu.devSphere.model.vo.friend.FriendContentVo;
import com.shutu.devSphere.model.vo.room.RoomVo;
import com.shutu.devSphere.model.vo.ws.response.ChatMessageResp;
import com.shutu.devSphere.service.MessageService;
import com.shutu.devSphere.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
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
     * 
     * @param roomQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public Result<Page<RoomVo>> listRoomVoByPage(@RequestBody RoomQueryRequest roomQueryRequest) {
        Page<RoomVo> roomVoPage = roomService.listRoomVoByPage(roomQueryRequest);
        return new Result<Page<RoomVo>>().ok(roomVoPage);
    }

    /**
     * 获取用户房间消息列表 (游标查询)
     * 
     * @param messageQueryRequest
     * @return
     */
    @PostMapping("/message/page/vo")
    public Result<CursorPage<ChatMessageResp>> listMessageVoByPage(
            @RequestBody MessageQueryRequest messageQueryRequest) {
        CursorPage<ChatMessageResp> cursorPage = messageService.listMessageVoByPage(messageQueryRequest);
        return new Result<CursorPage<ChatMessageResp>>().ok(cursorPage);
    }

    /**
     * 获取好友列表
     * 
     * @return
     */
    @PostMapping("/friend/list/vo")
    public Result<List<FriendContentVo>> listFriendContentVo() {
        List<FriendContentVo> list = roomService.listFriendContentVo();
        return new Result<List<FriendContentVo>>().ok(list);
    }

    /**
     * 创建群聊接口
     * 
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
     * 
     * @param searchRequestDTO
     * @return
     */
    @PostMapping("/search/addable")
    public Result<AddFriendVo> searchForAdd(@RequestBody SearchRequestDTO searchRequestDTO) {
        AddFriendVo addFriendVo = roomService.searchForAdd(searchRequestDTO.getQuery());
        return new Result<AddFriendVo>().ok(addFriendVo);
    }

    /**
     * 删除会话 (删除记录)
     */
    @DeleteMapping("/conversation")
    @Operation(summary = "删除会话")
    public Result<Void> deleteConversation(@RequestParam Long roomId) {
        Long loginUserId = SecurityUser.getUserId();
        roomService.deleteSession(roomId, loginUserId);
        return new Result<Void>().ok();
    }

    /**
     * 获取单个房间详情
     * 
     * @param roomId
     * @return
     */
    @GetMapping("/room/detail")
    @Operation(summary = "获取会话详情")
    public Result<RoomVo> getRoomDetail(@RequestParam Long roomId) {
        Long loginUserId = SecurityUser.getUserId();
        RoomVo roomVo = roomService.getRoomDetail(roomId, loginUserId);
        return new Result<RoomVo>().ok(roomVo);
    }

    /**
     * 清空聊天记录
     */
    @PostMapping("/history/clear")
    public Result<Void> clearHistory(@RequestParam Long roomId) {
        roomService.clearHistory(roomId, SecurityUser.getUserId());
        return new Result<Void>().ok();
    }

    /**
     * 搜索聊天记录
     */
    @GetMapping("/history/search")
    public Result<List<ChatMessageResp>> searchHistory(@RequestParam Long roomId, @RequestParam String keyword) {
        List<ChatMessageResp> list = messageService.searchHistory(roomId, keyword);
        return new Result<List<ChatMessageResp>>().ok(list);
    }

    /**
     * 撤回消息
     */
    @PostMapping("/message/recall")
    public Result<Void> recallMessage(@RequestParam Long messageId) {
        messageService.recallMessage(messageId);
        return new Result<Void>().ok();
    }
}
