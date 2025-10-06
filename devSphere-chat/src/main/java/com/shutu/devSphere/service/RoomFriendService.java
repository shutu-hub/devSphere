package com.shutu.devSphere.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.shutu.devSphere.model.entity.RoomFriend;


public interface RoomFriendService extends IService<RoomFriend> {

    /**
     * 获取房间好友
     *
     * @param uid 用户ID
     * @return {@link RoomFriend}
     */
    RoomFriend getRoomFriend(Long uid);

}
