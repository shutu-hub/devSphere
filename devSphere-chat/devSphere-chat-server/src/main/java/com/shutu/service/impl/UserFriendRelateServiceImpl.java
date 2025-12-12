package com.shutu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.mapper.RoomMapper;
import com.shutu.mapper.UserFriendRelateMapper;
import com.shutu.model.dto.friend.FriendDeleteDTO;
import com.shutu.model.dto.friend.FriendRemarkUpdateDTO;
import com.shutu.model.entity.Room;
import com.shutu.model.entity.RoomFriend;
import com.shutu.model.entity.UserFriendRelate;
import com.shutu.model.entity.UserRoomRelate;
import com.shutu.model.enums.chat.RoomTypeEnum;
import com.shutu.service.RoomFriendService;
import com.shutu.service.RoomService;
import com.shutu.service.UserFriendRelateService;
import com.shutu.service.UserRoomRelateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@RequiredArgsConstructor
@Service
public class UserFriendRelateServiceImpl extends ServiceImpl<UserFriendRelateMapper, UserFriendRelate>
    implements UserFriendRelateService {

    private final RoomMapper roomMapper;
    private final RoomFriendService roomFriendService;
    private final UserRoomRelateService userRoomRelateService;

    /**
     * 创建好友关系
     * 1. 创建 Room
     * 2. 创建 RoomFriend
     * 3. 创建 UserFriendRelate (双向)
     * 4. 创建 UserRoomRelate (双向)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFriendship(Long uid1, Long uid2) {
        // 1. 计算好友关系键
        Long smallerUid = Math.min(uid1, uid2);
        Long largerUid = Math.max(uid1, uid2);
        String roomKey = smallerUid + "_" + largerUid;
        // 查找是否已存在该好友对的房间
        RoomFriend existingRoomFriend = roomFriendService.getOne(
                new LambdaQueryWrapper<RoomFriend>().eq(RoomFriend::getRoomKey, roomKey)
        );
        Long roomIdToUse; // 最终要使用的房间ID

        // 2. 创建房间
        Room room = new Room();
        room.setType(RoomTypeEnum.PRIVATE.getType()); // 2=私聊
        room.setHotFlag(0);
        roomMapper.insert(room);
        roomIdToUse = room.getId();

        // 3. 处理 room_friend 表
        if (existingRoomFriend != null) {
            // 关系已存在 (被禁用)
            if (existingRoomFriend.getStatus() == 0) {
                // 已经是好友，但又触发了添加（理论上不应该，但做好幂等）
                throw new CommonException("已经是好友了", ErrorCode.BAD_REQUEST);
            }
            // 关系被禁用了 (status=1)，现在恢复
            existingRoomFriend.setStatus(0);
            existingRoomFriend.setRoomId(roomIdToUse); // 关联到新创建的房间
            roomFriendService.updateById(existingRoomFriend);
        } else {
            // 全新好友关系
            RoomFriend newRoomFriend = new RoomFriend();
            newRoomFriend.setRoomId(roomIdToUse);
            newRoomFriend.setUid1(smallerUid);
            newRoomFriend.setUid2(largerUid);
            newRoomFriend.setRoomKey(roomKey);
            newRoomFriend.setStatus(0);
            roomFriendService.save(newRoomFriend);
        }

        // 4. 创建好友关系
        UserFriendRelate relate1 = new UserFriendRelate();
        relate1.setUserId(uid1);
        relate1.setRelateId(uid2); // 存对方ID
        relate1.setRelateType(RoomTypeEnum.PRIVATE.getType()); // 2=好友
        UserFriendRelate relate2 = new UserFriendRelate();
        relate2.setUserId(uid2);
        relate2.setRelateId(uid1); // 存对方ID
        relate2.setRelateType(RoomTypeEnum.PRIVATE.getType());
        this.saveBatch(Arrays.asList(relate1, relate2));

        // 5. 创建用户房间关联
        UserRoomRelate userRoomRelate1 = new UserRoomRelate();
        userRoomRelate1.setUserId(uid1);
        userRoomRelate1.setRoomId(roomIdToUse);
        UserRoomRelate userRoomRelate2 = new UserRoomRelate();
        userRoomRelate2.setUserId(uid2);
        userRoomRelate2.setRoomId(roomIdToUse);
        userRoomRelateService.saveBatch(Arrays.asList(userRoomRelate1, userRoomRelate2));
    }

    /**
     * 检查是否为好友
     */
    @Override
    public boolean isFriend(Long userId, Long friendId) {
        return this.count(new LambdaQueryWrapper<UserFriendRelate>()
                .eq(UserFriendRelate::getUserId, userId)
                .eq(UserFriendRelate::getRelateId, friendId)
                .eq(UserFriendRelate::getRelateType, RoomTypeEnum.PRIVATE.getType())
        ) > 0;
    }

    /**
     * 修改好友备注
     */
    @Override
    public void updateRemark(FriendRemarkUpdateDTO dto) {
        Long loginUserId = SecurityUser.getUserId();

        // 备注是单向的，只修改自己对好友的备注
        this.update(new LambdaUpdateWrapper<UserFriendRelate>()
                .eq(UserFriendRelate::getUserId, loginUserId)
                .eq(UserFriendRelate::getRelateId, dto.getFriendId())
                .eq(UserFriendRelate::getRelateType, RoomTypeEnum.PRIVATE.getType())
                .set(UserFriendRelate::getRemark, dto.getRemark())
        );
    }

    /**
     * 删除好友（双向删除）
     * 1. 禁用 RoomFriend (逻辑删除)
     * 2. 删除 UserFriendRelate (双向物理删除)
     * 3. 删除 UserRoomRelate (双向物理删除)
     * 4. (可选) 删除 Room (物理删除)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFriend(FriendDeleteDTO dto) {
        Long loginUserId = SecurityUser.getUserId();
        Long friendId = dto.getFriendId();

        // 1. 找到私聊房间
        Long smallerUid = Math.min(loginUserId, friendId);
        Long largerUid = Math.max(loginUserId, friendId);
        RoomFriend roomFriend = roomFriendService.getOne(new LambdaQueryWrapper<RoomFriend>()
                .eq(RoomFriend::getUid1, smallerUid)
                .eq(RoomFriend::getUid2, largerUid)
        );

        if (roomFriend == null) {
            // 可能已经是陌生人，或者数据有误，直接删除关系表（幂等）
        } else {
            Long roomId = roomFriend.getRoomId();

            // 2. 禁用 RoomFriend (逻辑删除)
            roomFriend.setStatus(1); // 1 = 禁用
            roomFriendService.updateById(roomFriend);

            // 3. 删除 UserRoomRelate (双向)
            userRoomRelateService.remove(new LambdaQueryWrapper<UserRoomRelate>()
                    .eq(UserRoomRelate::getRoomId, roomId)
                    .in(UserRoomRelate::getUserId, loginUserId, friendId)
            );

            // 4. 物理删除 Room
            roomMapper.deleteById(roomId);
        }

        // 5. 删除 UserFriendRelate (双向)
        this.remove(new LambdaQueryWrapper<UserFriendRelate>()
                .eq(UserFriendRelate::getRelateType, RoomTypeEnum.PRIVATE.getType())
                .and(wrapper -> wrapper
                        .eq(UserFriendRelate::getUserId, loginUserId).eq(UserFriendRelate::getRelateId, friendId)
                )
                .or(wrapper -> wrapper
                        .eq(UserFriendRelate::getUserId, friendId).eq(UserFriendRelate::getRelateId, loginUserId)
                )
        );
    }
}




