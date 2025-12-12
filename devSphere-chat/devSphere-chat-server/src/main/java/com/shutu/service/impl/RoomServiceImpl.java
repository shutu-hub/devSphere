package com.shutu.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.commons.tools.utils.Result;
import com.shutu.manager.FriendSearchStrategy;
import com.shutu.model.dto.chat.GroupCreateRequestDTO;
import com.shutu.model.dto.group.GroupInviteRequestDTO;
import com.shutu.model.dto.group.GroupKickRequestDTO;
import com.shutu.model.dto.group.GroupUpdateRequestDTO;
import com.shutu.model.entity.*;
import com.shutu.model.enums.chat.MessageTypeEnum;
import com.shutu.model.vo.group.GroupDetailVo;
import com.shutu.model.vo.group.GroupMemberVo;
import com.shutu.dto.SysUserDTO;
import com.shutu.feign.UserFeignClient;
import com.shutu.manager.FriendSearchFacade;
import com.shutu.mapper.RoomMapper;
import com.shutu.model.dto.chat.RoomQueryRequest;
import com.shutu.model.dto.friend.FriendQueryRequest;
import com.shutu.model.enums.chat.FriendTargetTypeEnum;
import com.shutu.model.enums.chat.RoomTypeEnum;
import com.shutu.model.vo.friend.AddFriendVo;
import com.shutu.model.vo.friend.FriendContentVo;
import com.shutu.model.vo.room.RoomVo;
import com.shutu.service.MessageService;
import com.shutu.service.RoomFriendService;
import com.shutu.service.RoomGroupService;
import com.shutu.service.UserFriendRelateService;
import com.shutu.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl extends ServiceImpl<RoomMapper, Room>
        implements com.shutu.service.RoomService {

    private final com.shutu.service.UserRoomRelateService userRoomRelateService;
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
     * 
     * @param roomQueryRequest 房间查询请求
     * @return
     */
    @Override
    public Page<RoomVo> listRoomVoByPage(RoomQueryRequest roomQueryRequest) {
        int size = roomQueryRequest.getPageSize();
        int current = roomQueryRequest.getCurrent();
        // 当前登陆用户id
        Long loginUserId = SecurityUser.getUserId();

        // 1、查询用户下的房间
        Page<UserRoomRelate> page = userRoomRelateService.page(new Page<>(current, size),
                new LambdaQueryWrapper<UserRoomRelate>().eq(UserRoomRelate::getUserId, loginUserId)
                        .ne(UserRoomRelate::getIsDeleted, 1)
                        .orderByDesc(UserRoomRelate::getUpdateTime));
        List<UserRoomRelate> userRoomRelateList = page.getRecords();

        if (userRoomRelateList.isEmpty()) {
            return new Page<>(current, size, 0);
        }

        // 收集 RoomID
        Set<Long> roomIds = userRoomRelateList.stream()
                .map(UserRoomRelate::getRoomId)
                .collect(Collectors.toSet());

        // 2、批量查询房间信息
        List<Room> roomList = this.listByIds(roomIds);
        Map<Long, Room> roomMap = roomList.stream()
                .collect(Collectors.toMap(Room::getId, java.util.function.Function.identity()));

        // 3、批量查询最后一条消息
        Set<Long> lastMsgIds = roomList.stream()
                .map(Room::getLastMsgId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Message> messageMap = new HashMap<>();
        if (!lastMsgIds.isEmpty()) {
            List<Message> messages = messageService.listByIds(lastMsgIds);
            messageMap = messages.stream()
                    .collect(Collectors.toMap(Message::getId, java.util.function.Function.identity()));
        }

        // 4、批量查询群聊信息
        Map<Long, RoomGroup> roomGroupMap = new HashMap<>();

        // 5、批量查询私聊信息
        Map<Long, RoomFriend> roomFriendMap = new HashMap<>();

        // 分类房间ID
        Set<Long> groupRoomIds = new HashSet<>();
        Set<Long> privateRoomIds = new HashSet<>();

        for (Room room : roomList) {
            if (Objects.equals(room.getType(), RoomTypeEnum.GROUP.getType())) {
                groupRoomIds.add(room.getId());
            } else {
                privateRoomIds.add(room.getId());
            }
        }

        // 批量查群
        if (!groupRoomIds.isEmpty()) {
            List<RoomGroup> groups = roomGroupService.list(new LambdaQueryWrapper<RoomGroup>()
                    .in(RoomGroup::getRoomId, groupRoomIds));
            roomGroupMap = groups.stream()
                    .collect(Collectors.toMap(RoomGroup::getRoomId, java.util.function.Function.identity()));
        }

        // 批量查私聊
        Map<Long, UserDetail> userDetailMap = new HashMap<>();
        if (!privateRoomIds.isEmpty()) {
            List<RoomFriend> friends = roomFriendService.list(new LambdaQueryWrapper<RoomFriend>()
                    .in(RoomFriend::getRoomId, privateRoomIds));
            roomFriendMap = friends.stream()
                    .collect(Collectors.toMap(RoomFriend::getRoomId, java.util.function.Function.identity()));

            Set<Long> friendUserIds = friends.stream()
                    .map(f -> Objects.equals(f.getUid1(), loginUserId) ? f.getUid2() : f.getUid1())
                    .collect(Collectors.toSet());

            if (!friendUserIds.isEmpty()) {
                try {
                    com.shutu.commons.tools.utils.Result<List<SysUserDTO>> result = userFeignClient
                            .listByIds(new ArrayList<>(friendUserIds));
                    if (result.getData() != null) {
                        for (SysUserDTO dto : result.getData()) {
                            UserDetail u = new UserDetail();
                            u.setId(dto.getId());
                            u.setUsername(dto.getUsername());
                            u.setHeadUrl(dto.getHeadUrl());
                            userDetailMap.put(dto.getId(), u);
                        }
                    }
                } catch (Exception e) {
                    // 忽略异常，保持列表加载
                }
            }
        }

        // 组装结果
        List<RoomVo> roomVoList = new ArrayList<>();
        for (UserRoomRelate relate : userRoomRelateList) {
            Long roomId = relate.getRoomId();
            Room room = roomMap.get(roomId);
            if (room == null)
                continue;

            RoomVo roomVo = new RoomVo();
            roomVo.setId(roomId);
            roomVo.setType(room.getType());
            roomVo.setActiveTime(room.getActiveTime());

            // 消息内容
            Message msg = messageMap.get(room.getLastMsgId());
            if (msg != null) {
                roomVo.setContent(msg.getContent());
            } else {
                roomVo.setContent("暂无消息");
            }

            // 未读数
            if (relate.getLatestReadMsgId() != null) {
                long count = messageService.count(new LambdaQueryWrapper<Message>()
                        .eq(Message::getRoomId, roomId)
                        .gt(Message::getId, relate.getLatestReadMsgId()));
                roomVo.setUnreadNum((int) count);
            } else {
                long count = messageService.count(new LambdaQueryWrapper<Message>()
                        .eq(Message::getRoomId, roomId));
                roomVo.setUnreadNum((int) count);
            }

            // 填充详情
            if (Objects.equals(room.getType(), RoomTypeEnum.GROUP.getType())) {
                RoomGroup group = roomGroupMap.get(roomId);
                if (group != null) {
                    roomVo.setAvatar(group.getAvatar());
                    roomVo.setRoomName(group.getName());
                    roomVo.setUserId(group.getOwnerId());
                }
                // 成员数
                long count = userRoomRelateService
                        .count(new LambdaQueryWrapper<UserRoomRelate>().eq(UserRoomRelate::getRoomId, roomId));
                roomVo.setMemberCount((int) count);
            } else {
                RoomFriend friend = roomFriendMap.get(roomId);
                if (friend != null) {
                    Long friendId = Objects.equals(friend.getUid1(), loginUserId) ? friend.getUid2() : friend.getUid1();
                    UserDetail user = userDetailMap.get(friendId);
                    if (user != null) {
                        roomVo.setAvatar(user.getHeadUrl());
                        roomVo.setRoomName(user.getUsername());
                    }
                    roomVo.setUserId(friendId);
                }
            }
            roomVoList.add(roomVo);
        }

        Page<RoomVo> resultPage = new Page<>(current, size, page.getTotal());
        resultPage.setRecords(roomVoList);
        return resultPage;
    }

    @Override
    public List<FriendContentVo> listFriendContentVo() {
        // 获取当前登录用户的ID
        Long loginUserId = SecurityUser.getUserId();
        // 查询当前登录用户的所有好友关系
        List<UserFriendRelate> userFriendRelates = userFriendRelateService.list(
                new LambdaQueryWrapper<UserFriendRelate>().eq(UserFriendRelate::getUserId, loginUserId));

        List<FriendContentVo> friendContentVos = new ArrayList<>();
        Map<Integer, List<Long>> roomTypeMap = new HashMap<>();
        // 根据关系类型分组好友关系，以便后续处理
        for (UserFriendRelate userFriendRelate : userFriendRelates) {
            roomTypeMap.computeIfAbsent(userFriendRelate.getRelateType(), k -> new ArrayList<>())
                    .add(userFriendRelate.getRelateId());
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
                            .eq(UserFriendRelate::getRelateType, RoomTypeEnum.PRIVATE.getType()));
            vo.setFriendTarget(friendRelate != null ? FriendTargetTypeEnum.JOIN.getType()
                    : FriendTargetTypeEnum.UN_JOIN.getType());

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
                    new LambdaQueryWrapper<RoomGroup>().eq(RoomGroup::getRoomId, roomId));

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
                                .eq(UserRoomRelate::getRoomId, group.getRoomId()));
                vo.setFriendTarget(
                        relate != null ? FriendTargetTypeEnum.JOIN.getType() : FriendTargetTypeEnum.UN_JOIN.getType());

                return vo;
            }
        }

        // 3. 都没找到
        return null;
    }

    /**
     * 创建群聊
     * 1. 创建 Room (房间)
     * 2. 创建 RoomGroup (群详情)
     * 3. 批量插入 UserRoomRelate (成员进入房间)
     * 4. 批量插入 UserFriendRelate (成员的联系人列表出现群聊)
     * 5. 发送系统消息
     * 6. 回填 Room 活跃状态
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

        // 2. 创建房间 (dev_sphere_room)
        Room room = new Room();
        room.setType(RoomTypeEnum.GROUP.getType()); // 1=群聊
        room.setHotFlag(0); // 默认非热门
        this.save(room);
        Long newRoomId = room.getId();

        // 3. 创建群聊详情 (room_group)
        RoomGroup group = new RoomGroup();
        group.setRoomId(newRoomId);
        group.setOwnerId(ownerId);
        group.setName(dto.getName());
        group.setAvatar("https://api.dicebear.com/7.x/identicon/svg?seed=" + dto.getName());
        roomGroupService.save(group);

        // 4. 批量插入群成员 (user_room_relate) - 负责“进入房间”
        List<UserRoomRelate> relates = memberIds.stream()
                .map(userId -> {
                    UserRoomRelate relate = new UserRoomRelate();
                    relate.setUserId(userId);
                    relate.setRoomId(newRoomId);
                    return relate;
                })
                .collect(Collectors.toList());
        userRoomRelateService.saveBatch(relates);

        // 4.5 批量插入群聊关系 (user_friend_relate) - 负责“联系人列表显示”
        List<UserFriendRelate> friendRelates = memberIds.stream()
                .map(userId -> {
                    UserFriendRelate friendRelate = new UserFriendRelate();
                    friendRelate.setUserId(userId);
                    friendRelate.setRelateId(newRoomId); // 关系ID存的是 RoomID
                    friendRelate.setRelateType(RoomTypeEnum.GROUP.getType()); // 1 = 群聊
                    return friendRelate;
                })
                .collect(Collectors.toList());
        userFriendRelateService.saveBatch(friendRelates);

        // 5. 发送一条系统消息 (dev_sphere_message)
        Message initialMessage = new Message();
        initialMessage.setRoomId(newRoomId);
        initialMessage.setFromUid(ownerId); // 用群主身份发送
        initialMessage.setContent(ownerName + " 创建了群聊");
        initialMessage.setType(MessageTypeEnum.TEXT.getType()); // 1 = 正常文本
        messageService.save(initialMessage);

        // 6. 回填房间的最后活跃时间和消息ID
        // (确保 messageService.save 后 initialMessage 能拿到 ID 和 createTime)
        room.setLastMsgId(initialMessage.getId());
        room.setActiveTime(initialMessage.getCreateTime());
        this.updateById(room);

        // 7. 构造 RoomVo 返回给前端 (保持不变)
        RoomVo newRoomVo = new RoomVo();
        newRoomVo.setId(newRoomId);
        newRoomVo.setType(RoomTypeEnum.GROUP.getType());
        newRoomVo.setAvatar(group.getAvatar());
        newRoomVo.setRoomName(group.getName());
        newRoomVo.setContent(initialMessage.getContent());
        newRoomVo.setActiveTime(initialMessage.getCreateTime());
        newRoomVo.setUnreadNum(0);
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

    /**
     * 隐藏会话 (保留历史)
     */
    @Override
    public void hideSession(Long roomId, Long userId) {
        boolean updated = userRoomRelateService.update(new LambdaUpdateWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, userId)
                .set(UserRoomRelate::getIsDeleted, 1)); // 仅标记不可见

        if (!updated) {
            throw new CommonException("操作失败", ErrorCode.DATA_NOT_EXIST);
        }
    }

    /**
     * 删除会话 (清空历史)
     */
    @Override
    public void deleteSession(Long roomId, Long userId) {
        // 1. 获取当前房间最新的消息ID
        Room room = this.getById(roomId);
        Long lastMsgId = (room != null && room.getLastMsgId() != null) ? room.getLastMsgId() : Long.MAX_VALUE;

        // 2. 更新：标记删除 + 设置 minMsgId 为当前最新消息
        boolean updated = userRoomRelateService.update(new LambdaUpdateWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, userId)
                .set(UserRoomRelate::getIsDeleted, 1)
                .set(UserRoomRelate::getMinMsgId, lastMsgId)); // 以后只看 lastMsgId 之后的消息

        if (!updated) {
            throw new CommonException("操作失败", ErrorCode.DATA_NOT_EXIST);
        }
    }

    /**
     * 获取单个房间详情
     */
    @Override
    public RoomVo getRoomDetail(Long roomId, Long userId) {
        // 1. 验证用户是否在房间内 (忽略 is_deleted，只要关系存在就能查)
        UserRoomRelate relate = userRoomRelateService.getOne(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, userId));

        if (relate == null) {
            throw new CommonException("您不在该房间中", ErrorCode.FORBIDDEN);
        }

        // 2. 查房间基础信息
        Room room = this.getById(roomId);
        if (room == null) {
            throw new CommonException("房间不存在", ErrorCode.DATA_NOT_EXIST);
        }

        RoomVo roomVo = new RoomVo();
        roomVo.setId(roomId);
        roomVo.setType(room.getType());
        roomVo.setActiveTime(room.getActiveTime());

        // 3. 填充消息内容 (考虑 minMsgId)
        Long lastMsgId = room.getLastMsgId();
        Message message = (lastMsgId != null) ? messageService.getById(lastMsgId) : null;
        Long minMsgId = (relate.getMinMsgId() != null) ? relate.getMinMsgId() : 0L;

        if (message != null && message.getId() > minMsgId) {
            roomVo.setContent(message.getContent());
        } else {
            roomVo.setContent("");
        }

        // 4. 未读数
        Long readMsgId = relate.getLatestReadMsgId() != null ? relate.getLatestReadMsgId() : 0L;
        Long effectiveReadId = Math.max(readMsgId, minMsgId);
        long unreadCount = messageService.count(new LambdaQueryWrapper<Message>()
                .eq(Message::getRoomId, roomId)
                .gt(Message::getId, effectiveReadId));
        roomVo.setUnreadNum((int) unreadCount);

        // 5. 填充名称和头像
        if (Objects.equals(room.getType(), RoomTypeEnum.GROUP.getType())) {
            RoomGroup roomGroup = roomGroupService
                    .getOne(new LambdaQueryWrapper<RoomGroup>().eq(RoomGroup::getRoomId, roomId));
            long count = userRoomRelateService
                    .count(new LambdaQueryWrapper<UserRoomRelate>().eq(UserRoomRelate::getRoomId, roomId));
            roomVo.setMemberCount((int) count);
            if (roomGroup != null) {
                roomVo.setAvatar(roomGroup.getAvatar());
                roomVo.setRoomName(roomGroup.getName());
                roomVo.setUserId(roomGroup.getOwnerId());
            }
        } else {
            RoomFriend roomFriend = roomFriendService
                    .getOne(new LambdaQueryWrapper<RoomFriend>().eq(RoomFriend::getRoomId, roomId));
            if (roomFriend != null) {
                Long friendId = Objects.equals(roomFriend.getUid1(), userId) ? roomFriend.getUid2()
                        : roomFriend.getUid1();
                com.shutu.commons.tools.utils.Result<com.shutu.commons.security.user.UserDetail> res = userFeignClient
                        .getById(friendId);
                if (res.getData() != null) {
                    roomVo.setAvatar(res.getData().getHeadUrl());
                    roomVo.setRoomName(res.getData().getUsername());
                }
                roomVo.setUserId(friendId);
            }
        }

        return roomVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void quitGroup(Long roomId) {
        Long userId = SecurityUser.getUserId();
        String username = SecurityUser.getUser().getUsername();

        // 1. 检查群是否存在
        RoomGroup group = roomGroupService.getOne(new LambdaQueryWrapper<RoomGroup>()
                .eq(RoomGroup::getRoomId, roomId));
        if (group == null) {
            throw new CommonException("群聊不存在", ErrorCode.GROUP_NOT_FOUND);
        }

        // 2. 检查是否是群主
        if (group.getOwnerId().equals(userId)) {
            throw new CommonException("群主不能退出群聊，请先转让群主或解散群聊", ErrorCode.FORBIDDEN);
        }

        // 3. 删除房间成员关系
        boolean removed = userRoomRelateService.remove(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, userId));
        if (!removed) {
            throw new CommonException("您不在该群聊中", ErrorCode.DATA_NOT_EXIST);
        }

        // 4. 删除好友列表中的群聊关系
        userFriendRelateService.remove(new LambdaQueryWrapper<UserFriendRelate>()
                .eq(UserFriendRelate::getUserId, userId)
                .eq(UserFriendRelate::getRelateId, roomId)
                .eq(UserFriendRelate::getRelateType, RoomTypeEnum.GROUP.getType()));

        // 5. 发送退出消息
        Message msg = new Message();
        msg.setRoomId(roomId);
        msg.setFromUid(userId);
        msg.setType(MessageTypeEnum.TEXT.getType()); // 或者系统消息类型
        msg.setContent(username + " 退出了群聊");
        messageService.save(msg);

        // 更新房间最后活跃时间
        Room room = this.getById(roomId);
        if (room != null) {
            room.setLastMsgId(msg.getId());
            room.setActiveTime(msg.getCreateTime());
            this.updateById(room);
        }
    }

    @Override
    public void clearHistory(Long roomId, Long userId) {
        // 1. 获取当前房间最新的消息ID
        Room room = this.getById(roomId);
        Long lastMsgId = (room != null && room.getLastMsgId() != null) ? room.getLastMsgId() : Long.MAX_VALUE;

        // 2. 更新：设置 minMsgId 为当前最新消息 (不改变 isDeleted 状态)
        boolean updated = userRoomRelateService.update(new LambdaUpdateWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, userId)
                .set(UserRoomRelate::getMinMsgId, lastMsgId));

        if (!updated) {
            throw new CommonException("操作失败", ErrorCode.DATA_NOT_EXIST);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inviteToGroup(GroupInviteRequestDTO dto) {
        Long loginUserId = SecurityUser.getUserId();
        String username = SecurityUser.getUser().getUsername();
        Long roomId = dto.getRoomId();
        List<Long> userIds = dto.getUserIds();

        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // 1. 检查群是否存在
        RoomGroup group = roomGroupService.getOne(new LambdaQueryWrapper<RoomGroup>()
                .eq(RoomGroup::getRoomId, roomId));
        if (group == null) {
            throw new CommonException("群聊不存在", ErrorCode.GROUP_NOT_FOUND);
        }

        // 2. 检查邀请人是否在群内
        UserRoomRelate inviterRelate = userRoomRelateService.getOne(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, loginUserId));
        if (inviterRelate == null) {
            throw new CommonException("您不在该群聊中，无法邀请成员", ErrorCode.FORBIDDEN);
        }

        // 3. 过滤掉已经在群内的用户
        List<UserRoomRelate> existingRelates = userRoomRelateService.list(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .in(UserRoomRelate::getUserId, userIds));
        Set<Long> existingUserIds = existingRelates.stream()
                .map(UserRoomRelate::getUserId)
                .collect(Collectors.toSet());

        List<Long> newUserIds = userIds.stream()
                .filter(uid -> !existingUserIds.contains(uid))
                .collect(Collectors.toList());

        if (newUserIds.isEmpty()) {
            return;
        }

        // 4. 批量添加成员 (user_room_relate)
        List<UserRoomRelate> newRelates = newUserIds.stream()
                .map(userId -> {
                    UserRoomRelate relate = new UserRoomRelate();
                    relate.setUserId(userId);
                    relate.setRoomId(roomId);
                    return relate;
                })
                .collect(Collectors.toList());
        userRoomRelateService.saveBatch(newRelates);

        // 5. 批量添加群聊关系 (user_friend_relate)
        List<UserFriendRelate> newFriendRelates = newUserIds.stream()
                .map(userId -> {
                    UserFriendRelate friendRelate = new UserFriendRelate();
                    friendRelate.setUserId(userId);
                    friendRelate.setRelateId(roomId);
                    friendRelate.setRelateType(RoomTypeEnum.GROUP.getType());
                    return friendRelate;
                })
                .collect(Collectors.toList());
        userFriendRelateService.saveBatch(newFriendRelates);

        // 6. 发送系统消息
        // 获取被邀请人的名字 (为了消息友好)
        Result<List<SysUserDTO>> usersRes = userFeignClient.listByIds(newUserIds);
        String invitedNames = "新成员";
        if (usersRes.getData() != null) {
            invitedNames = usersRes.getData().stream()
                    .map(SysUserDTO::getRealName)
                    .limit(3)
                    .collect(Collectors.joining(", "));
            if (usersRes.getData().size() > 3) {
                invitedNames += " 等";
            }
        }

        Message msg = new Message();
        msg.setRoomId(roomId);
        msg.setFromUid(loginUserId);
        msg.setType(MessageTypeEnum.TEXT.getType());
        msg.setContent(username + " 邀请 " + invitedNames + " 加入群聊");
        messageService.save(msg);

        // 更新房间活跃时间
        Room room = this.getById(roomId);
        if (room != null) {
            room.setLastMsgId(msg.getId());
            room.setActiveTime(msg.getCreateTime());
            this.updateById(room);
            room.setLastMsgId(msg.getId());
            room.setActiveTime(msg.getCreateTime());
            this.updateById(room);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickFromGroup(GroupKickRequestDTO dto) {
        Long loginUserId = SecurityUser.getUserId();
        String username = SecurityUser.getUser().getUsername();
        Long roomId = dto.getRoomId();
        Long targetUid = dto.getUid();

        // 1. 检查群是否存在
        RoomGroup group = roomGroupService.getOne(new LambdaQueryWrapper<RoomGroup>()
                .eq(RoomGroup::getRoomId, roomId));
        if (group == null) {
            throw new CommonException("群聊不存在", ErrorCode.GROUP_NOT_FOUND);
        }

        // 2. 权限校验：必须是群主
        if (!group.getOwnerId().equals(loginUserId)) {
            throw new CommonException("权限不足，只有群主才能移出成员", ErrorCode.FORBIDDEN);
        }

        // 3. 不能移出自己
        if (targetUid.equals(loginUserId)) {
            throw new CommonException("不能移出群主自己", ErrorCode.BAD_REQUEST);
        }

        // 4. 移出成员
        boolean removed = userRoomRelateService.remove(new LambdaQueryWrapper<UserRoomRelate>()
                .eq(UserRoomRelate::getRoomId, roomId)
                .eq(UserRoomRelate::getUserId, targetUid));

        if (!removed) {
            return;
        }

        // 5. 删除好友列表中的群聊关系
        userFriendRelateService.remove(new LambdaQueryWrapper<UserFriendRelate>()
                .eq(UserFriendRelate::getUserId, targetUid)
                .eq(UserFriendRelate::getRelateId, roomId)
                .eq(UserFriendRelate::getRelateType, RoomTypeEnum.GROUP.getType()));

        // 6. 发送系统消息
        Result<UserDetail> userRes = userFeignClient.getById(targetUid);
        String targetName = (userRes.getData() != null) ? userRes.getData().getRealName() : "成员";

        Message msg = new Message();
        msg.setRoomId(roomId);
        msg.setFromUid(loginUserId);
        msg.setType(MessageTypeEnum.TEXT.getType());
        msg.setContent(username + " 将 " + targetName + " 移出了群聊");
        messageService.save(msg);

        // 更新房间活跃时间
        Room room = this.getById(roomId);
        if (room != null) {
            room.setLastMsgId(msg.getId());
            room.setActiveTime(msg.getCreateTime());
            this.updateById(room);
        }
    }
}
