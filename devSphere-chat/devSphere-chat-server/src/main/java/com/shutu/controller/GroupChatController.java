package com.shutu.controller;

import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.validator.ValidatorUtils;
import com.shutu.commons.tools.validator.group.DefaultGroup;
import com.shutu.model.dto.chat.GroupCreateRequestDTO;
import com.shutu.model.dto.group.GroupInviteRequestDTO;
import com.shutu.model.dto.group.GroupKickRequestDTO;
import com.shutu.model.dto.group.GroupUpdateRequestDTO;
import com.shutu.model.vo.group.GroupDetailVo;
import com.shutu.model.vo.group.GroupMemberVo;
import com.shutu.model.vo.room.RoomVo;
import com.shutu.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/group")
@Tag(name = "群聊管理")
@RequiredArgsConstructor
public class GroupChatController {

    private final RoomService roomService;

    @PostMapping("/create")
    @Operation(summary = "创建群聊")
    public Result<RoomVo> createGroup(@RequestBody GroupCreateRequestDTO dto) throws Exception {
        ValidatorUtils.validateEntity(dto, DefaultGroup.class);
        RoomVo newRoom = roomService.createGroup(dto);
        return new Result<RoomVo>().ok(newRoom);
    }

    @GetMapping("/detail")
    @Operation(summary = "获取群聊详情")
    public Result<GroupDetailVo> getGroupDetail(@Parameter(description = "房间ID") @RequestParam Long roomId) {
        GroupDetailVo detail = roomService.getGroupDetail(roomId);
        return new Result<GroupDetailVo>().ok(detail);
    }

    @GetMapping("/members")
    @Operation(summary = "获取群成员列表")
    public Result<List<GroupMemberVo>> getGroupMembers(@Parameter(description = "房间ID") @RequestParam Long roomId) {
        List<GroupMemberVo> members = roomService.getGroupMembers(roomId);
        return new Result<List<GroupMemberVo>>().ok(members);
    }

    @PutMapping("/update")
    @Operation(summary = "修改群信息")
    public Result updateGroupInfo(@RequestBody GroupUpdateRequestDTO dto) throws Exception {
        ValidatorUtils.validateEntity(dto, DefaultGroup.class);
        roomService.updateGroupInfo(dto);
        return new Result();
    }

    @PostMapping("/quit")
    @Operation(summary = "退出群聊")
    public Result quitGroup(@Parameter(description = "房间ID") @RequestParam Long roomId) {
        roomService.quitGroup(roomId);
        return new Result();
    }

    @PostMapping("/invite")
    @Operation(summary = "邀请进群")
    public Result inviteToGroup(@RequestBody GroupInviteRequestDTO dto) throws Exception {
        ValidatorUtils.validateEntity(dto, DefaultGroup.class);
        roomService.inviteToGroup(dto);
        return new Result();
    }

    @PostMapping("/kick")
    @Operation(summary = "移出群成员")
    public Result kickFromGroup(@RequestBody GroupKickRequestDTO dto) throws Exception {
        ValidatorUtils.validateEntity(dto, DefaultGroup.class);
        roomService.kickFromGroup(dto);
        return new Result();
    }
}