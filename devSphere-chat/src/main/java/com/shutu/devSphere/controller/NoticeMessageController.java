package com.shutu.devSphere.controller;

import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.utils.Result;
import com.shutu.devSphere.model.dto.chat.MessageNoticeUpdateRequest;
import com.shutu.devSphere.model.dto.friend.FriendAddRequest;
import com.shutu.devSphere.sse.SseServer;
import lombok.RequiredArgsConstructor;
import com.shutu.devSphere.model.vo.message.MessageNumVo;
import com.shutu.devSphere.model.vo.message.NoticeMessageVo;
import com.shutu.devSphere.service.NoticeMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

import static com.shutu.commons.tools.constant.Constant.USER_KEY;

/**
 * 消息通知
 */
@RestController
@RequestMapping("/notice")
@Slf4j
@RequiredArgsConstructor
public class NoticeMessageController {
    private final NoticeMessageService noticeMessageService;

    /**
     * 添加好友通知请求
     * @param friendAddRequest
     * @return
     */
    @PostMapping("/add/friend")
    public Result<Boolean> addFriend(@RequestBody FriendAddRequest friendAddRequest) {
        noticeMessageService.addFriend(friendAddRequest);
        return new Result<Boolean>().ok(true);
    }

    /**
     * 连接
     * 用户SSE连接
     * 它返回一个SseEmitter实例，这时候连接就已经创建了.
     *
     * @return {@link SseEmitter}
     */
    @GetMapping("/userConnect")
    public SseEmitter connect(String token) {
        //一般取登录用户账号作为 messageId。分组的话需要约定 messageId的格式。
        UserDetail user = SecurityUser.getUser();
        String userId = USER_KEY + user.getId();
        return SseServer.createConnect(userId);
    }

    /**
     * 获取消息数量
     * @return
     */
    @GetMapping("/messageNum")
    public Result<MessageNumVo> getMessageNum() {
        return new Result<MessageNumVo>().ok(noticeMessageService.getMessageNum());
    }


    /**
     * 获取消息列表
     * @return
     */
    @GetMapping("/messageNotice/list")
    public Result<List<NoticeMessageVo>> getMessageNoticeList() {
        return new Result<List<NoticeMessageVo>>().ok(noticeMessageService.getMessageNoticeList());
    }


    /**
     * 消息已读
     * @param id
     * @return
     */
    @GetMapping("/messageNotice/read")
    public Result<Boolean> readMessageNotice(Long id) {
        return  new Result<Boolean>().ok(noticeMessageService.readMessageNotice(id));
    }


    /**
     * 消息处理
     * @param noticeUpdateRequest
     * @return
     */
    @PostMapping("/messageNotice/handle")
    public Result<String> handleMessageNotice(@RequestBody MessageNoticeUpdateRequest noticeUpdateRequest) {
        return  new Result<String>().ok(noticeMessageService.handleMessageNotice(noticeUpdateRequest));
    }

}
