package com.shutu.devSphere.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.commons.tools.utils.Result;
import com.shutu.devSphere.manager.FriendSearchStrategy;
import com.shutu.devSphere.model.dto.chat.GroupCreateRequestDTO;
import com.shutu.devSphere.model.dto.group.GroupUpdateRequestDTO;
import com.shutu.devSphere.model.vo.group.GroupDetailVo;
import com.shutu.devSphere.model.vo.group.GroupMemberVo;
import com.shutu.dto.SysUserDTO;
import com.shutu.feign.UserFeignClient;
import com.shutu.devSphere.manager.FriendSearchFacade;
import com.shutu.devSphere.mapper.RoomMapper;
import com.shutu.devSphere.model.dto.chat.RoomQueryRequest;
import com.shutu.devSphere.model.dto.friend.FriendQueryRequest;
import com.shutu.devSphere.model.entity.*;
import com.shutu.devSphere.model.enums.chat.FriendSearchTypeEnum;
import com.shutu.devSphere.model.enums.chat.FriendTargetTypeEnum;
import com.shutu.devSphere.model.enums.chat.RoomTypeEnum;
import com.shutu.devSphere.model.vo.friend.AddFriendVo;
import com.shutu.devSphere.model.vo.friend.FriendContentVo;
import com.shutu.devSphere.model.vo.room.RoomVo;
import com.shutu.devSphere.service.*;
import com.shutu.devSphere.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl extends ServiceImpl<RoomMapper, Room>
        implements RoomService {

    private final UserRoomRelateService userRoomRelateService;
    private final MessageService messageService;
    private final RoomFriendService roomFriendService;
    private final RoomGroupService roomGroupService;
    private final UserFriendRelateService userFriendRelateService;
    private final FriendSearchFacade friendSearchFacade;
    private final UserFeignClient userFeignClient;
    // Spring会自动识别并注入所有实现
    private final List<FriendSearchStrategy> friendSearchStrategies;

    /**
     * 分页查询所有房间信息
     * @param roomQueryRequest 房间查询请求
     * @return
     */
    @Override
    public Page<RoomVo> listRoomVoByPage(RoomQueryRequest roomQueryRequest) {
        int size = roomQueryRequest.getPageSize();
        int current = roomQueryRequest.getCurrent();
        //当前登陆用户id
        Long loginUserId = SecurityUser.getUserId();

        //1、查询用户下的房间，后面可改为游标查询
        Page<UserRoomRelate> page = userRoomRelateService.page(new Page<>(current, size),
                new LambdaQueryWrapper<UserRoomRelate>().eq(UserRoomRelate::getUserId, loginUserId)
                        .orderByDesc(UserRoomRelate::getUpdateTime));
        List<UserRoomRelate> userRoomRelateList = page.getRecords();

        if (userRoomRelateList.isEmpty()) {
            return new Page<>(current, size, 0); // 如果没有房间，直接返回空分页
        }

        //房间id集合
        Map<Long, RoomVo> roomVoMap = new HashMap<>();
        userRoomRelateList.forEach(item -> {
            Long roomId = item.getRoomId();
            RoomVo roomVo = new RoomVo();
            roomVo.setId(roomId);
            roomVoMap.put(roomId, roomVo);
        });

        //2、查询房间信息
        List<Room> roomList = this.listByIds(roomVoMap.keySet());
        for (Room item : roomList) {
            RoomVo roomVo = roomVoMap.get(item.getId());
            if (roomVo == null) continue; // 健壮性检查

            Long lastMsgId = item.getLastMsgId();
            //3、查询数据信息
            Message message = (lastMsgId != null) ? messageService.getById(lastMsgId) : null;
            if (message != null) {
                // 设置会话中的最新消息
                roomVo.setContent(message.getContent());
            } else {
                roomVo.setContent("暂无消息"); // 默认值
            }
            // 设置会话最后更新时间
            roomVo.setActiveTime(item.getActiveTime());
            roomVo.setType(item.getType());

            //判断房间类型
            if (Objects.equals(item.getType(), RoomTypeEnum.GROUP.getType())) {
                // 群聊
                RoomGroup roomGroup = roomGroupService.getOne(new LambdaQueryWrapper<RoomGroup>().eq(RoomGroup::getRoomId, item.getId()));

                // 查询群成员总数
                long count = userRoomRelateService.count(new LambdaQueryWrapper<UserRoomRelate>().eq(UserRoomRelate::getRoomId, item.getId()));
                roomVo.setMemberCount((int) count);

                if(roomGroup != null) {
                    roomVo.setAvatar(roomGroup.getAvatar());
                    roomVo.setRoomName(roomGroup.getName());
                    roomVo.setUserId(roomGroup.getOwnerId()); // 群聊时 userId 代表群主ID
                }
            } else {
                //4、查询私聊房间信息
                RoomFriend roomFriend = roomFriendService.getOne(new LambdaQueryWrapper<RoomFriend>().eq(RoomFriend::getRoomId, item.getId()));
                if(roomFriend != null) {
                    Long userId = Objects.equals(roomFriend.getUid1(), loginUserId) ? roomFriend.getUid2() : roomFriend.getUid1();
                    //5、查询好友信息
                    Result<UserDetail> userDetailResult = userFeignClient.getById(userId);
                    UserDetail user = userDetailResult.getData();
                    if (user != null) {
                        roomVo.setAvatar(user.getHeadUrl());
                        roomVo.setRoomName(user.getUsername());
                    }
                    roomVo.setUserId(userId); // 私聊时 userId 代表对方ID
                }
            }
        }

        Page<RoomVo> roomVoPage = new Page<>(current, size, page.getTotal());
        // 按照 userRoomRelateList 的顺序（即最后更新时间）重新排序
        List<RoomVo> orderedRoomVoList = userRoomRelateList.stream()
                .map(relate -> roomVoMap.get(relate.getRoomId()))
                .filter(Objects::nonNull) // 过滤掉可能查询失败的房间
                .collect(Collectors.toList());
        roomVoPage.setRecords(orderedRoomVoList);
        return roomVoPage;
    }

    @Override
    public List<FriendContentVo> listFriendContentVo() {
        // 获取当前登录用户的ID
        Long loginUserId = SecurityUser.getUserId();
        // 查询当前登录用户的所有好友关系
        List<UserFriendRelate> userFriendRelates = userFriendRelateService.list(
                new LambdaQueryWrapper<UserFriendRelate>().eq(UserFriendRelate::getUserId, loginUserId)
        );

        List<FriendContentVo> friendContentVos = new ArrayList<>();
        Map<Integer, List<Long>> roomTypeMap = new HashMap<>();
        // 根据关系类型分组好友关系，以便后续处理
        for (UserFriendRelate userFriendRelate : userFriendRelates) {
            roomTypeMap.computeIfAbsent(userFriendRelate.getRelateType(), k -> new ArrayList<>()).add(userFriendRelate.getRelateId());
        }
        // 遍历分组后的关系类型，为每种关系类型调用搜索服务，将结果添加到返回列表中
        roomTypeMap.keySet().forEach(item -> {
            FriendContentVo friendContentVo = friendSearchFacade.searchAll(item, roomTypeMap.get(item));
            friendContentVos.add(friendContentVo);
        });
        return friendContentVos;
    }


    /**
     * 用于 strategies 内部调用
     */
    @Override
    public AddFriendVo searchFriendVoById(FriendQueryRequest friendQueryRequest) {
        String id = friendQueryRequest.getId();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID不能为空");
        }
        // 参数验证（保留了你原来的工具类）
        if (!CommonUtils.isNumeric(id) && !CommonUtils.isNumericExceptLastS(id)) {
            throw new IllegalArgumentException("ID格式不合法");
        }

        // 查找支持的策略并执行搜索
        return friendSearchStrategies.stream()
                .filter(strategy -> strategy.supports(id))
                .findFirst()
                .map(strategy -> strategy.search(id))
                .orElse(null);
    }

    /**
     * 统一搜索接口实现
     * 1. 尝试按用户名搜好友
     * 2. 如果搜不到，且输入的是数字，尝试按群ID搜群聊
     */
    @Override
    public AddFriendVo searchForAdd(String query) {
        Long loginUserId = SecurityUser.getUserId();

        // 1. 尝试按用户名搜索用户
        SysUserDTO user = userFeignClient.getByUsername(query).getData();

        if (user != null) {
            // 找到了用户，封装 AddFriendVo
            AddFriendVo vo = new AddFriendVo();
            vo.setUid(user.getId());
            vo.setName(user.getRealName()); // 使用真实姓名
            vo.setAvatar(user.getHeadUrl());
            vo.setType(RoomTypeEnum.PRIVATE.getType()); // 2 = 私聊

            // 检查是否已经是好友
            UserFriendRelate friendRelate = userFriendRelateService.getOne(
                    new LambdaQueryWrapper<UserFriendRelate>()
                            .eq(UserFriendRelate::getUserId, loginUserId)
                            .eq(UserFriendRelate::getRelateId, user.getId())
                            .eq(UserFriendRelate::getRelateType, RoomTypeEnum.PRIVATE.getType())
            );
            vo.setFriendTarget(friendRelate != null ? FriendTargetTypeEnum.JOIN.getType() : FriendTargetTypeEnum.UN_JOIN.getType());

            return vo;
        }

        // 2. 如果不是用户，且输入的是纯数字，尝试按群ID搜索
        if (CommonUtils.isNumeric(query)) {
            Long roomId;
            try {
                roomId = Long.parseLong(query);
            } catch (NumberFormatException e) {
                return null; // 无法解析为数字
            }

            RoomGroup group = roomGroupService.getOne(
                    new LambdaQueryWrapper<RoomGroup>().eq(RoomGroup::getRoomId, roomId)
            );

            if (group != null) {
                AddFriendVo vo = new AddFriendVo();
                vo.setRoomId(group.getRoomId());
                vo.setName(group.getName());
                vo.setAvatar(group.getAvatar());
                vo.setType(RoomTypeEnum.GROUP.getType()); // 1 = 群聊

                // 检查是否已在群聊
                UserRoomRelate relate = userRoomRelateService.getOne(
                        new LambdaQueryWrapper<UserRoomRelate>()
                                .eq(UserRoomRelate::getUserId, loginUserId)
                                .eq(UserRoomRelate::getRoomId, group.getRoomId())
                );
                vo.setFriendTarget(relate != null ? FriendTargetTypeEnum.JOIN.getType() : FriendTargetTypeEnum.UN_JOIN.getType());

                return vo;
            }
        }

        // 3. 都没找到
        return null;
    }


    /**
     * 创建群聊
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomVo createGroup(GroupCreateRequestDTO dto) {
        Long ownerId = SecurityUser.getUserId();
        String ownerName = SecurityUser.getUser().getUsername();

        // 1. 准备群成员列表 (必须包含群主自己)
        List<Long> memberIds = new ArrayList<>(dto.getUserIds());
        if (!memberIds.contains(ownerId)) {
            memberIds.add(ownerId);
        }

        // 2. 创建房间
        Room room = new Room();
        room.setType(RoomTypeEnum.GROUP.getType());
        room.setHotFlag(0); // 默认非热门
        // activeTime 和 lastMsgId 先不设置，等待创建初始消息后再更新
        this.save(room);
        Long newRoomId = room.getId();

        // 3. 创建群聊详情
        RoomGroup group = new RoomGroup();
        group.setRoomId(newRoomId);
        group.setOwnerId(ownerId);
        group.setName(dto.getName());
        // 自动生成一个默认头像
        group.setAvatar("https://api.dicebear.com/7.x/identicon/svg?seed=" + dto.getName());
        roomGroupService.save(group);

        // 4. 批量插入群成员
        List<UserRoomRelate> relates = memberIds.stream()
                .map(userId -> {
                    UserRoomRelate relate = new UserRoomRelate();
                    relate.setUserId(userId);
                    relate.setRoomId(newRoomId);
                    // createTime 和 updateTime 会自动填充
                    return relate;
                })
                .collect(Collectors.toList());
        userRoomRelateService.saveBatch(relates);

        // 5. 发送一条系统消息
        Message initialMessage = new Message();
        initialMessage.setRoomId(newRoomId);
        initialMessage.setFromUid(ownerId); // 系统消息，也可以用 0 或 ownerId
        initialMessage.setContent(ownerName + " 创建了群聊");
        initialMessage.setType(1); // 1 = 正常文本 (未来可以设为 100=系统通知)
        messageService.save(initialMessage);

        // 6. 回填房间的最后活跃时间和消息ID
        // 确保 `messageService.save` 后 initialMessage 能拿到 ID 和 createTime
        room.setLastMsgId(initialMessage.getId());
        room.setActiveTime(initialMessage.getCreateTime());
        this.updateById(room);

        // 7. 构造 RoomVo 返回给前端
        RoomVo newRoomVo = new RoomVo();
        newRoomVo.setId(newRoomId);
        newRoomVo.setType(RoomTypeEnum.GROUP.getType());
        newRoomVo.setAvatar(group.getAvatar());
        newRoomVo.setRoomName(group.getName());
        newRoomVo.setContent(initialMessage.getContent());
        newRoomVo.setActiveTime(initialMessage.getCreateTime());
        newRoomVo.setUnreadNum(0); // 新创建的群，对自己而言没有未读
        newRoomVo.setUserId(ownerId); // 群聊时 userId 代表群主ID

        return newRoomVo;
    }


    /**
     * 获取群聊详情
     */
    @Override
    public GroupDetailVo getGroupDetail(Long roomId) {
        // 1. 验证用户是否在群内
        Long loginUserId = SecurityUser.getUserId();
        UserRoomRelate relate = userRoomRelateService.getOne(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, loginUserId));
        if (relate == null) {
            throw new CommonException("您不在该群聊中，无法查看详情", ErrorCode.FORBIDDEN);
        }

        // 2. 查询群基本信息
        RoomGroup roomGroup = roomGroupService.getOne(new LambdaQueryWrapper<RoomGroup>()
                .eq(RoomGroup::getRoomId, roomId));
        if (roomGroup == null) {
            throw new CommonException("群聊不存在或已解散", ErrorCode.GROUP_NOT_FOUND);
        }

        // 3. 查询群成员总数
        long memberCount = userRoomRelateService.count(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId));

        // 4. 组装VO
        GroupDetailVo vo = new GroupDetailVo();
        BeanUtils.copyProperties(roomGroup, vo);
        vo.setMemberCount((int) memberCount);
        return vo;
    }


    /**
     * 获取群成员列表
     */
    @Override
    public List<GroupMemberVo> getGroupMembers(Long roomId) {
        Long loginUserId = SecurityUser.getUserId();

        // 1. 验证用户是否在群内
        UserRoomRelate relate = userRoomRelateService.getOne(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, loginUserId));
        if (relate == null) {
            throw new CommonException("您不在该群聊中，无法查看成员", ErrorCode.FORBIDDEN);
        }

        // 2. 查询群信息（获取群主ID）
        RoomGroup roomGroup = roomGroupService.getOne(new LambdaQueryWrapper<RoomGroup>()
                .eq(RoomGroup::getRoomId, roomId));
        if (roomGroup == null) {
            throw new CommonException("群聊不存在", ErrorCode.GROUP_NOT_FOUND);
        }

        // 3. 查询所有群成员的ID
        List<UserRoomRelate> memberRelates = userRoomRelateService.list(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId));
        List<Long> memberIds = memberRelates.stream()
                .map(UserRoomRelate::getUserId)
                .collect(Collectors.toList());

        if (memberIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 4. 批量查询用户信息
        Result<List<SysUserDTO>> listResult = userFeignClient.listByIds(memberIds);
        List<SysUserDTO> users = listResult.getData();

        // 5. 组装VO
        return users.stream().map(user -> {
            GroupMemberVo memberVo = new GroupMemberVo();
            memberVo.setUid(user.getId());
            memberVo.setUsername(user.getRealName()); // 优先使用真实姓名
            memberVo.setAvatar(user.getHeadUrl());
            if (user.getId().equals(roomGroup.getOwnerId())) {
                memberVo.setIsOwner(1); // 标记群主
            }
            return memberVo;
        }).collect(Collectors.toList());
    }


    /**
     * 更新群信息 (带权限校验)
     */
    @Override
    @Transactional
    public void updateGroupInfo(GroupUpdateRequestDTO dto) {
        Long loginUserId = SecurityUser.getUserId();
        Long roomId = dto.getRoomId();

        // 1. 查找群
        RoomGroup roomGroup = roomGroupService.getOne(new LambdaQueryWrapper<RoomGroup>()
                .eq(RoomGroup::getRoomId, roomId));
        if (roomGroup == null) {
            throw new CommonException("群聊不存在", ErrorCode.GROUP_NOT_FOUND);
        }

        // 2. 权限校验：必须是群主
        if (!roomGroup.getOwnerId().equals(loginUserId)) {
            throw new CommonException("权限不足，只有群主才能修改群信息", ErrorCode.FORBIDDEN);
        }

        // 3. 更新信息
        if (dto.getName() != null) {
            roomGroup.setName(dto.getName());
        }

        roomGroupService.updateById(roomGroup);
    }

}




