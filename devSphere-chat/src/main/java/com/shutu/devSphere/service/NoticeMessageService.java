package com.shutu.devSphere.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.shutu.devSphere.model.dto.chat.MessageNoticeUpdateRequest;
import com.shutu.devSphere.model.dto.friend.FriendAddRequest;
import com.shutu.devSphere.model.entity.NoticeMessage;
import com.shutu.devSphere.model.vo.message.MessageNumVo;
import com.shutu.devSphere.model.vo.message.NoticeMessageVo;
import java.util.List;


public interface NoticeMessageService extends IService<NoticeMessage> {

    /**
     * 添加好友
     *
     * @param friendAddRequest 好友添加请求
     */
    void addFriend(FriendAddRequest friendAddRequest);

    /**
     * 获取消息数量
     * @return {@link MessageNumVo}
     */
    MessageNumVo getMessageNum();

    /**
     * 获取消息通知列表
     * @return {@link List}<{@link NoticeMessageVo}>
     */
    List<NoticeMessageVo> getMessageNoticeList();

    /**
     * @param id 消息通知id
     * @return {@link Boolean}
     */
    Boolean readMessageNotice(Long id);

    /**
     * 处理消息通知
     * @param noticeUpdateRequest 消息通知更新请求
     * @return {@link String}
     */
    String handleMessageNotice(MessageNoticeUpdateRequest noticeUpdateRequest);
}
