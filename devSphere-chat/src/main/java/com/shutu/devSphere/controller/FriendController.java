package com.shutu.devSphere.controller;

import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.validator.ValidatorUtils;
import com.shutu.devSphere.model.dto.friend.FriendDeleteDTO;
import com.shutu.devSphere.model.dto.friend.FriendRemarkUpdateDTO;
import com.shutu.devSphere.service.UserFriendRelateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 好友关系管理（备注、删除）
 */
@RestController
@RequestMapping("/friend")
@Tag(name = "好友管理")
@RequiredArgsConstructor
public class FriendController {

    private final UserFriendRelateService userFriendRelateService;

    /**
     * 修改好友备注
     */
    @PutMapping("/remark")
    @Operation(summary = "修改好友备注")
    public Result updateRemark(@RequestBody FriendRemarkUpdateDTO dto) throws Exception {
        ValidatorUtils.validateEntity(dto);
        userFriendRelateService.updateRemark(dto);
        return new Result();
    }

    /**
     * 删除好友
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除好友")
    public Result deleteFriend(@RequestBody FriendDeleteDTO dto) throws Exception {
        ValidatorUtils.validateEntity(dto);
        userFriendRelateService.deleteFriend(dto);
        return new Result();
    }
}