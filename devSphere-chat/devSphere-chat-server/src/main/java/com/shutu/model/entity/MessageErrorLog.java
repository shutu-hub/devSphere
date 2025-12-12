package com.shutu.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * IM消息死信日志实体
 */
@Data
@TableName("dev_sphere_message_error_log")
public class MessageErrorLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 前端临时ID
     */
    private String tempId;

    /**
     * 发送者ID
     */
    private Long fromUid;

    /**
     * 目标ID
     */
    private Long targetId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型
     */
    private Integer type;

    /**
     * 原始Redis消息体 (JSON字符串)
     */
    private String originalPayload;

    /**
     * 错误原因
     */
    private String errorMessage;

    /**
     * 处理状态 0-待处理 1-已修复 2-忽略
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}