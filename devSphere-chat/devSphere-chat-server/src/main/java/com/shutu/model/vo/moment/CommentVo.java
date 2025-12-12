package com.shutu.model.vo.moment;

import lombok.Data;
import java.util.Date;

@Data
public class CommentVo {
    private Long id;
    private Long postId;
    private Long userId;
    private String username;
    private String avatar;
    private String content;
    private Long replyToUserId;
    private String replyToUsername;
    private Date createTime;
}
