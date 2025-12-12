package com.shutu.model.vo.moment;

import lombok.Data;

import java.util.List;

@Data
public class MomentResp {
    private Long id;
    private Long userId;
    private String content;
    private List<String> imageUrls;
    private Integer likeCount;
    private Integer commentCount;
    private String createdAt;
    private UserVo user;

    // 互动详情
    private List<UserVo> likes;
    private List<CommentVo> comments;
    private Boolean isLiked; // 当前用户是否点赞
}