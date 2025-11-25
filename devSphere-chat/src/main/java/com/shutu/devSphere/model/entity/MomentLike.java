package com.shutu.devSphere.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

/**
 * 动态点赞
 */
@Data
@TableName("moment_like")
public class MomentLike {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("post_id")
    private Long postId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableLogic
    private Integer deleted;
}
