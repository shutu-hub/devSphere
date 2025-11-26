package com.shutu.devSphere.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.devSphere.model.dto.chat.GroupCreateRequestDTO;
import com.shutu.devSphere.model.dto.chat.RoomQueryRequest;
import com.shutu.devSphere.model.dto.friend.FriendQueryRequest;
import com.shutu.devSphere.model.dto.group.GroupUpdateRequestDTO;
import com.shutu.devSphere.model.entity.Room;
import com.shutu.devSphere.model.entity.UserRoomRelate;
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
     * 
     * @param dto
     * @return 新群聊的 RoomVo
     */
    RoomVo createGroup(GroupCreateRequestDTO dto);

    GroupDetailVo getGroupDetail(Long roomId);

    List<GroupMemberVo> getGroupMembers(Long roomId);

    void updateGroupInfo(GroupUpdateRequestDTO dto);

    AddFriendVo searchForAdd(String query);

    AddFriendVo searchFriendVoById(FriendQueryRequest friendQueryRequest);

    /**
     * 隐藏会话
     */
    public void hideSession(Long roomId, Long userId);

    /**
     * 删除会话 (清空历史)
     */
    public void deleteSession(Long roomId, Long userId);

    /**
     * 获取单个房间的会话详情 (用于点击联系人进入隐藏会话)
     * 
     * @param roomId 房间ID
     * @param userId 当前用户ID
     * @return RoomVo
     */
    RoomVo getRoomDetail(Long roomId, Long userId);

    /**
     * 退出群聊
     * 
     * @param roomId 房间ID
     */
    /**
     * 退出群聊
     * 
     * @param roomId 房间ID
     */
    void quitGroup(Long roomId);

    /**
     * 清空聊天记录
     * 
     * @param roomId 房间ID
     * @param userId 用户ID
     */
    void clearHistory(Long roomId, Long userId);
}
