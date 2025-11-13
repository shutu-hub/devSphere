package com.shutu.devSphere.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shutu.devSphere.model.dto.chat.GroupCreateRequestDTO;
import com.shutu.devSphere.model.dto.chat.RoomQueryRequest;
import com.shutu.devSphere.model.dto.friend.FriendQueryRequest;
import com.shutu.devSphere.model.dto.group.GroupUpdateRequestDTO;
import com.shutu.devSphere.model.entity.Room;
import com.shutu.devSphere.model.vo.friend.AddFriendVo;
import com.shutu.devSphere.model.vo.friend.FriendContentVo;
import com.shutu.devSphere.model.vo.group.GroupDetailVo;
import com.shutu.devSphere.model.vo.group.GroupMemberVo;
import com.shutu.devSphere.model.vo.room.RoomVo;

import java.util.List;


public interface RoomService extends IService<Room> {

    /**
     * 按页面列出房间 VO
     *
     * @param roomQueryRequest 房间查询请求
     * @return {@link Page}<{@link RoomVo}>
     */
    Page<RoomVo> listRoomVoByPage(RoomQueryRequest roomQueryRequest);

    /**
     * 列出好友内容 vo
     *
     * @return {@link List}<{@link FriendContentVo}>
     */
    List<FriendContentVo> listFriendContentVo();


    /**
     * 创建群聊
     * @param dto
     * @return 新群聊的 RoomVo
     */
    RoomVo createGroup(GroupCreateRequestDTO dto);

    GroupDetailVo getGroupDetail(Long roomId);

    List<GroupMemberVo> getGroupMembers(Long roomId);

    void updateGroupInfo(GroupUpdateRequestDTO dto);

    AddFriendVo searchForAdd(String query);

    AddFriendVo searchFriendVoById(FriendQueryRequest friendQueryRequest);
}
