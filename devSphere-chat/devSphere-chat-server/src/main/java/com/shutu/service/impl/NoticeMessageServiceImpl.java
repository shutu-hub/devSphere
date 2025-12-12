package com.shutu.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.commons.tools.utils.Result;
import com.shutu.model.vo.ws.response.WSBaseResp;
import com.shutu.feign.UserFeignClient;
import com.shutu.mapper.NoticeMessageMapper;
import com.shutu.model.dto.chat.MessageNoticeUpdateRequest;
import com.shutu.model.dto.friend.FriendAddRequest;
import com.shutu.model.entity.NoticeMessage;
import com.shutu.model.enums.ProcessResultTypeEnum;
import com.shutu.model.enums.chat.NoticeTypeEnum;
import com.shutu.model.enums.chat.ReadTargetTypeEnum;
import com.shutu.model.enums.chat.RoomTypeEnum;
import com.shutu.model.enums.ws.WSReqTypeEnum;
import com.shutu.model.vo.message.ChatMessageVo;
import com.shutu.model.vo.message.MessageNumVo;
import com.shutu.model.vo.message.NoticeMessageVo;
import com.shutu.service.NoticeMessageService;
import com.shutu.service.UserFriendRelateService;
import com.shutu.websocket.service.WebSocketService;
import com.shutu.sse.SseServer;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import static com.shutu.commons.tools.constant.Constant.USER_KEY;
import static com.shutu.constant.MessageConstant.ADD_USER_MESSAGE;

@Service
@RequiredArgsConstructor
public class NoticeMessageServiceImpl extends ServiceImpl<NoticeMessageMapper, NoticeMessage>
        implements NoticeMessageService {

    @Resource
    private UserFeignClient userFeignClient;
    @Resource
    private WebSocketService webSocketService;
    // 确保 UserFriendRelateService 已注入
    private final UserFriendRelateService userFriendRelateService;

    @Override
    @Transactional
    public void addFriend(FriendAddRequest friendAddRequest) {
        Long senderId = SecurityUser.getUserId();
        Long toUserId = friendAddRequest.getUserId();

        if (Objects.equals(senderId, toUserId)) {
            throw new CommonException("不能添加自己为好友", ErrorCode.BAD_REQUEST);
        }

        // 1. 检查是否已经是好友
        if (userFriendRelateService.isFriend(senderId, toUserId)) {
            throw new CommonException("对方已经是您的好友，请勿重复添加", ErrorCode.BAD_REQUEST);
        }

        // 2. 检查是否已存在待处理的申请 (防止重复发送)
        long pendingCount = this.count(new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getUserId, senderId)
                .eq(NoticeMessage::getToUserId, toUserId)
                .eq(NoticeMessage::getNoticeType, NoticeTypeEnum.USER.getType())
                .eq(NoticeMessage::getProcessResult, String.valueOf(ProcessResultTypeEnum.PENDING.getType()))
        );

        if (pendingCount > 0) {
            throw new CommonException("已发送好友申请，请耐心等待对方处理", ErrorCode.BAD_REQUEST);
        }

        // 创建通知消息对象，并设置相关信息
        NoticeMessage noticeMessage = new NoticeMessage();
        noticeMessage.setUserId(senderId);
        noticeMessage.setNoticeType(NoticeTypeEnum.USER.getType());
        noticeMessage.setToUserId(toUserId);
        noticeMessage.setNoticeContent(friendAddRequest.getRemark());
        noticeMessage.setReadTarget(ReadTargetTypeEnum.UN_READ.getType()); // 0 未读
        noticeMessage.setProcessResult(String.valueOf(ProcessResultTypeEnum.PENDING.getType())); // 0 待处理

        // 保存通知消息到数据库
        boolean save = this.save(noticeMessage);
        if (!save) {
            throw new CommonException("添加好友失败",ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 检查目标用户是否在线，如果在线，则发送实时通知
        if (SseServer.containUser(USER_KEY + toUserId)) {
            // 构造用于发送给客户端的通知消息Vo对象
            NoticeMessageVo noticeMessageVo = new NoticeMessageVo();
            noticeMessageVo.setNoticeType(NoticeTypeEnum.USER.getType());
            noticeMessageVo.setNoticeContent(friendAddRequest.getRemark());
            noticeMessageVo.setUserId(SecurityUser.getUserId());

            UserDetail user = SecurityUser.getUser();
            String userName = user.getUsername();
            noticeMessageVo.setAvatar(user.getHeadUrl());
            noticeMessageVo.setName(userName);
            noticeMessageVo.setTitle(userName + "请求添加您为好友");

            SseServer.sendMessage(USER_KEY + toUserId, JSONUtil.toJsonStr(noticeMessageVo));
        }
    }


    @Override
    public MessageNumVo getMessageNum() {
        Long userId = SecurityUser.getUserId();
        long count = this.count(new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getToUserId, userId)
                .eq(NoticeMessage::getReadTarget, ReadTargetTypeEnum.UN_READ.getType()));
        MessageNumVo messageNumVo = new MessageNumVo();
        messageNumVo.setNoticeNum(count);
        return messageNumVo;
    }


    @Override
    public List<NoticeMessageVo> getMessageNoticeList() {
        List<NoticeMessage> noticeMessageList = this.list(new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getToUserId, SecurityUser.getUserId())
                .orderByDesc(NoticeMessage::getCreateTime));

        return noticeMessageList.stream().map(item -> {
            NoticeMessageVo noticeMessageVo = new NoticeMessageVo();
            BeanUtils.copyProperties(item, noticeMessageVo);
            Result<UserDetail> userDetailResult = userFeignClient.getById(item.getUserId());
            UserDetail user = userDetailResult.getData();
            noticeMessageVo.setAvatar(user.getHeadUrl());
            noticeMessageVo.setName(user.getUsername());

            if (Objects.equals(item.getNoticeType(), NoticeTypeEnum.USER.getType())) {
                noticeMessageVo.setTitle(user.getUsername() + "请求添加您为好友");
            }
            return noticeMessageVo;
        }).collect(Collectors.toList());
    }


    @Override
    public Boolean readMessageNotice(Long id) {
        NoticeMessage noticeMessage = this.getById(id);
        if (noticeMessage == null) {
            throw new CommonException("消息不存在",ErrorCode.INTERNAL_SERVER_ERROR);
        }
        noticeMessage.setReadTarget(ReadTargetTypeEnum.READ.getType());
        return this.updateById(noticeMessage);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleMessageNotice(MessageNoticeUpdateRequest noticeUpdateRequest) {
        NoticeMessage noticeMessage = this.getOne(new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getId, noticeUpdateRequest.getId())
                .eq(NoticeMessage::getToUserId, SecurityUser.getUserId()));

        if (noticeMessage == null) {
            throw new CommonException("消息不存在" , ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 检查是否已处理
        if (!Objects.equals(noticeMessage.getProcessResult(), String.valueOf(ProcessResultTypeEnum.PENDING.getType()))) {
            throw new CommonException("该申请已处理，请勿重复操作", ErrorCode.BAD_REQUEST);
        }

        String desc = " ";
        if (Objects.equals(NoticeTypeEnum.of(noticeMessage.getNoticeType()), NoticeTypeEnum.USER)) {

            ProcessResultTypeEnum resultType = ProcessResultTypeEnum.of(noticeUpdateRequest.getProcessResult());
            if (resultType == null) {
                throw new CommonException("无效的处理结果类型", ErrorCode.BAD_REQUEST);
            }

            desc = resultType.getDesc();
            noticeMessage.setProcessResult(desc); // 存储描述文本
            noticeMessage.setReadTarget(ReadTargetTypeEnum.READ.getType());

            if (resultType == ProcessResultTypeEnum.AGREE) {
                //调用完整的创建好友关系链
                userFriendRelateService.createFriendship(noticeMessage.getUserId(), noticeMessage.getToUserId());

                // 向申请人发送“已同意”的回执消息
                Long applicantUserId = noticeMessage.getUserId();
                ChatMessageVo chatMessageVo = new ChatMessageVo();
                chatMessageVo.setType(RoomTypeEnum.PRIVATE.getType()); // 私聊
                chatMessageVo.setContent(ADD_USER_MESSAGE); // "我们已经是好友了..."
                WSBaseResp<ChatMessageVo> wsBaseResp = new WSBaseResp<>();
                wsBaseResp.setType(WSReqTypeEnum.CHAT.getType());
                wsBaseResp.setData(chatMessageVo);
                webSocketService.sendToUid(wsBaseResp, applicantUserId);
            }
        }

        this.updateById(noticeMessage);

        return desc;
    }


}