package com.shutu.devSphere.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.commons.tools.utils.Result;
import com.shutu.devSphere.model.vo.ws.response.WSBaseResp;
import com.shutu.feign.UserFeignClient;
import com.shutu.devSphere.mapper.NoticeMessageMapper;
import com.shutu.devSphere.model.dto.chat.MessageNoticeUpdateRequest;
import com.shutu.devSphere.model.dto.friend.FriendAddRequest;
import com.shutu.devSphere.model.entity.NoticeMessage;
import com.shutu.devSphere.model.entity.UserFriendRelate;
import com.shutu.devSphere.model.enums.ProcessResultTypeEnum;
import com.shutu.devSphere.model.enums.chat.MessageTypeEnum;
import com.shutu.devSphere.model.enums.chat.NoticeTypeEnum;
import com.shutu.devSphere.model.enums.chat.ReadTargetTypeEnum;
import com.shutu.devSphere.model.enums.chat.RoomTypeEnum;
import com.shutu.devSphere.model.enums.ws.WSReqTypeEnum;
import com.shutu.devSphere.model.vo.message.ChatMessageVo;
import com.shutu.devSphere.model.vo.message.MessageNumVo;
import com.shutu.devSphere.model.vo.message.NoticeMessageVo;
import com.shutu.devSphere.model.vo.ws.request.WSBaseReq;
import com.shutu.devSphere.service.NoticeMessageService;
import com.shutu.devSphere.service.UserFriendRelateService;
import com.shutu.devSphere.sse.SseServer;
import com.shutu.devSphere.websocket.service.WebSocketService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static com.shutu.commons.tools.constant.Constant.USER_KEY;
import static com.shutu.devSphere.constant.MessageConstant.ADD_USER_MESSAGE;

@Service
public class NoticeMessageServiceImpl extends ServiceImpl<NoticeMessageMapper, NoticeMessage>
        implements NoticeMessageService {

    @Resource
    private UserFeignClient userFeignClient;

    @Resource
    private WebSocketService webSocketService;

    @Resource
    private UserFriendRelateService userFriendRelateService;

    @Override
    public void addFriend(FriendAddRequest friendAddRequest) {
        // 根据好友添加请求获取备注信息和请求用户ID
        String remark = friendAddRequest.getRemark();
        Long toUserId = friendAddRequest.getUserId();

        // 创建通知消息对象，并设置相关信息
        NoticeMessage noticeMessage = new NoticeMessage();
        noticeMessage.setUserId(SecurityUser.getUserId());
        noticeMessage.setNoticeType(NoticeTypeEnum.USER.getType());
        noticeMessage.setToUserId(toUserId);
        noticeMessage.setNoticeContent(remark);

        // 保存通知消息到数据库
        boolean save = this.save(noticeMessage);
        // 如果保存失败，抛出业务异常
        if (!save) {
            throw new CommonException("添加好友失败",ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 检查目标用户是否在线，如果在线，则发送实时通知
        if (SseServer.containUser(USER_KEY + toUserId)) {
            // 构造用于发送给客户端的通知消息Vo对象
            NoticeMessageVo noticeMessageVo = new NoticeMessageVo();
            noticeMessageVo.setNoticeType(NoticeTypeEnum.USER.getType());
            noticeMessageVo.setNoticeContent(remark);
            noticeMessageVo.setUserId(SecurityUser.getUserId());

            // 获取当前登录用户的头像和用户名，用于通知显示
            UserDetail user = SecurityUser.getUser();
            String userName = user.getUsername();
            noticeMessageVo.setAvatar(user.getHeadUrl());
            noticeMessageVo.setName(userName);
            noticeMessageVo.setTitle(userName + "请求添加您为好友");

            // 向目标用户发送通知消息
            SseServer.sendMessage(USER_KEY + toUserId, JSONUtil.toJsonStr(noticeMessageVo));
        }


    }

    @Override
    public MessageNumVo getMessageNum() {
        // 获取当前登录用户的ID
        Long userId = SecurityUser.getUserId();
        // 获取未读消息数量 后面考虑使用Redis缓存
        long count = this.count(new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getToUserId, userId)
                .eq(NoticeMessage::getReadTarget, ReadTargetTypeEnum.UN_READ.getType()));

        // 创建消息数量Vo对象
        MessageNumVo messageNumVo = new MessageNumVo();
        messageNumVo.setNoticeNum(count);

        return messageNumVo;
    }

    @Override
    public List<NoticeMessageVo> getMessageNoticeList() {
        // 根据当前登录用户的ID查询通知消息列表，并按创建时间降序排序
        List<NoticeMessage> noticeMessageList = this.list(new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getToUserId, SecurityUser.getUserId())
                .orderByDesc(NoticeMessage::getCreateTime));

        // 遍历通知消息列表，转换为NoticeMessageVo对象，并设置发送者的头像和名称
        return noticeMessageList.stream().map(item -> {
            NoticeMessageVo noticeMessageVo = new NoticeMessageVo();
            BeanUtils.copyProperties(item, noticeMessageVo); // 复制基础属性
            Result<UserDetail> userDetailResult = userFeignClient.getById(item.getUserId());
            UserDetail user = userDetailResult.getData();
            noticeMessageVo.setAvatar(user.getHeadUrl()); // 设置发送者头像
            noticeMessageVo.setName(user.getUsername()); // 设置发送者名称

            // 如果通知类型为用户添加好友请求，则设置特定的标题
            if (item.getNoticeType().equals(NoticeTypeEnum.USER.getType())) {
                noticeMessageVo.setTitle(user.getUsername() + "请求添加您为好友");
            }

            return noticeMessageVo;
        }).collect(Collectors.toList());
    }

    @Override
    public Boolean readMessageNotice(Long id) {
        // 根据通知消息ID查询通知消息
        NoticeMessage noticeMessage = this.getById(id);
        // 如果通知消息不存在，抛出业务异常
        if (noticeMessage == null) {
            throw new CommonException("消息不存在",ErrorCode.INTERNAL_SERVER_ERROR);
        }
        //修改消息状态
        noticeMessage.setReadTarget(ReadTargetTypeEnum.READ.getType());
        // 更新通知消息的状态
        return this.updateById(noticeMessage);
    }

    @Override
    public String handleMessageNotice(MessageNoticeUpdateRequest noticeUpdateRequest) {
        // 根据通知消息ID查询通知消息
        NoticeMessage noticeMessage = this.getOne(new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getId, noticeUpdateRequest.getId())
                .eq(NoticeMessage::getToUserId, SecurityUser.getUserId()));
        // 如果通知消息不存在，抛出业务异常
        if (noticeMessage == null) {
            throw new CommonException("消息不存在" , ErrorCode.INTERNAL_SERVER_ERROR);
        }
        // 初始化描述信息为空格
        String desc = " ";
        // 判断通知类型是否为用户类型
        if (NoticeTypeEnum.of(noticeMessage.getNoticeType()) == NoticeTypeEnum.USER) {
            // 根据处理结果获取描述信息，并更新通知消息的处理结果
            desc = ProcessResultTypeEnum.of(noticeUpdateRequest.getProcessResult()).getDesc();
            noticeMessage.setProcessResult(desc);
            noticeMessage.setReadTarget(ReadTargetTypeEnum.READ.getType());
            if (noticeUpdateRequest.getProcessResult().equals(ProcessResultTypeEnum.AGREE.getType())) {
                // 发送请求的用户id
                Long userId = noticeMessage.getUserId();
                // 如果处理结果为同意
                saveUserRelate(noticeMessage);
                // 构建聊天消息体
                ChatMessageVo chatMessageVo = new ChatMessageVo();
                chatMessageVo.setType(MessageTypeEnum.PRIVATE.getType());
                chatMessageVo.setContent(ADD_USER_MESSAGE);
                // 构建WebSocket消息基础请求
                WSBaseResp<ChatMessageVo> wsBaseResp = new WSBaseResp<>();
                wsBaseResp.setType(WSReqTypeEnum.CHAT.getType());
                // 将聊天消息体序列化为JSON字符串，设置为消息数据
                wsBaseResp.setData(chatMessageVo);
                // 发送WebSocket消息
                webSocketService.sendToUid(wsBaseResp,userId);
            }

        }

        this.updateById(noticeMessage);

        return desc;
    }

    private void saveUserRelate(NoticeMessage noticeMessage) {
        // 创建用户和朋友的关系列表
        ArrayList<UserFriendRelate> userFriendRelates = new ArrayList<>();

        userFriendRelates.add(createUserFriendRelate(noticeMessage.getUserId(), noticeMessage.getToUserId(), RoomTypeEnum.PRIVATE.getType()));
        userFriendRelates.add(createUserFriendRelate(noticeMessage.getToUserId(), noticeMessage.getUserId(), RoomTypeEnum.PRIVATE.getType()));

        // 批量保存用户和朋友的关系
        userFriendRelateService.saveBatch(userFriendRelates);

    }
    private UserFriendRelate createUserFriendRelate(Long userId, Long friendId, Integer relateType) {
        UserFriendRelate relate = new UserFriendRelate();
        relate.setUserId(userId);
        relate.setRelateId(friendId);
        relate.setRelateType(relateType);
        return relate;
    }

}




