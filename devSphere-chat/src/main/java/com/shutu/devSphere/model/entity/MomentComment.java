package com.shutu.devSphere.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

/**
 * 动态评论
 */
@Data
@TableName("moment_comment")
public class MomentComment {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 所属动态ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("post_id")
    private Long postId;

    /**
     * 评论用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 回复评论 ID ，如果是一级评论则为 null
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("reply_to_comment_id")
    private Long replyToCommentId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 软删
     */
    @TableLogic
    private Integer deleted;
}
