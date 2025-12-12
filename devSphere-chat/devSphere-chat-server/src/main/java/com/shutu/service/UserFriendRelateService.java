package com.shutu.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.shutu.model.dto.friend.FriendDeleteDTO;
import com.shutu.model.dto.friend.FriendRemarkUpdateDTO;
import com.shutu.model.entity.UserFriendRelate;


public interface UserFriendRelateService extends IService<UserFriendRelate> {
    /**
     * (新增) 创建好友关系（事务性）
     * 这将处理所有相关的表
     * @param uid1 用户1
     * @param uid2 用户2
     */
    void createFriendship(Long uid1, Long uid2);

    /**
     * 检查是否为好友
     * @param userId
     * @param friendId
     * @return
     */
    boolean isFriend(Long userId, Long friendId);

    /**
     * 修改好友备注
     */
    void updateRemark(FriendRemarkUpdateDTO dto);

    /**
     * 删除好友
     */
    void deleteFriend(FriendDeleteDTO dto);
}
