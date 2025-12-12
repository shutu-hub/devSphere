package com.shutu.manager;

import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.utils.Result;
import com.shutu.model.entity.RoomFriend;
import com.shutu.model.enums.chat.FriendSearchTypeEnum;
import com.shutu.model.enums.chat.FriendTargetTypeEnum;
import com.shutu.model.vo.friend.AddFriendVo;
import com.shutu.service.RoomFriendService;
import com.shutu.util.CommonUtils;
import com.shutu.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户搜索策略
 */
@Component
@RequiredArgsConstructor
public class UserSearchStrategy implements FriendSearchStrategy {
    
    private final UserFeignClient userFeignClient;
    private final RoomFriendService roomFriendService;
    
    @Override
    public boolean supports(String id) {
        return CommonUtils.isNumeric(id);
    }
    
    @Override
    public AddFriendVo search(String id) {
        Long uid = Long.valueOf(id);
        Result<UserDetail> userDetailResult = userFeignClient.getById(uid);
        UserDetail user = userDetailResult.getData();
        if (user == null) {
            return null;
        }
        RoomFriend roomFriend = roomFriendService.getRoomFriend(uid);
        return getAddFriendVo(user, roomFriend);
    }
    
    private AddFriendVo getAddFriendVo(UserDetail user, RoomFriend roomFriend) {
        AddFriendVo addFriendVo = new AddFriendVo();
        addFriendVo.setAvatar(user.getHeadUrl());
        addFriendVo.setUid(user.getId());
        addFriendVo.setType(FriendSearchTypeEnum.FRIEND.getType());
        addFriendVo.setName(user.getUsername());
        if (roomFriend != null) {
            addFriendVo.setRoomId(roomFriend.getRoomId());
            addFriendVo.setFriendTarget(FriendTargetTypeEnum.JOIN.getType());
        }
        return addFriendVo;
    }
}
