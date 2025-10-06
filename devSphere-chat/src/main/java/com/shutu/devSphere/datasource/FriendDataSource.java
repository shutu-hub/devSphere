package com.shutu.devSphere.datasource;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.tools.utils.Result;
import com.shutu.dto.SysUserDTO;
import com.shutu.feign.UserFeignClient;
import com.shutu.devSphere.model.entity.RoomFriend;
import com.shutu.devSphere.model.enums.chat.FriendSearchTypeEnum;
import com.shutu.devSphere.model.vo.friend.FriendContentVo;
import com.shutu.devSphere.model.vo.friend.FriendVo;
import com.shutu.devSphere.service.RoomFriendService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 好友数据源
 */
@Component
public class FriendDataSource implements DataSource {
    @Resource
    private UserFeignClient userFeignClient;
    @Resource
    private RoomFriendService roomFriendService;

    @Override
    public FriendContentVo doSearch(List<Long> ids) {
        //获取登录用户id
        Long loginUserId = SecurityUser.getUserId();
        // 创建一个FriendContentVo实例
        FriendContentVo friendContentVo = new FriendContentVo();

        // 根据提供的ids，查询用户列表
        Result<List<SysUserDTO>> listResult = userFeignClient.listByIds(ids);
        List<SysUserDTO> userList = listResult.getData();
        // 设置FriendContentVo的类型和类型名称
        friendContentVo.setType(FriendSearchTypeEnum.FRIEND.getType());
        friendContentVo.setTypeName(FriendSearchTypeEnum.FRIEND.getDesc());

        // 将用户列表转换为朋友信息列表
        List<FriendVo> friendVoList = userList.stream().map(item -> {
            FriendVo friendVo = new FriendVo();
            // 设置头像
            friendVo.setAvatar(item.getHeadUrl());
            // 设置用户名
            friendVo.setName(item.getUsername());
            // 设置用户ID
            friendVo.setUid(item.getId());
            //获取房间ID
            long uid1 = item.getId() > loginUserId ? loginUserId : item.getId();
            long uid2 = item.getId() > loginUserId ? item.getId() : loginUserId;
            RoomFriend roomFriend = roomFriendService.getOne(new LambdaQueryWrapper<RoomFriend>().eq(RoomFriend::getUid1, uid1).eq(RoomFriend::getUid2, uid2));
            // 设置房间ID
            if (roomFriend != null) {
                friendVo.setRoomId(roomFriend.getRoomId());
            }
            return friendVo;
        }).collect(Collectors.toList());

        // 将朋友信息列表设置到FriendContentVo中
        friendContentVo.setContent(friendVoList);

        // 返回FriendContentVo实例
        return friendContentVo;

    }
}
