package com.shutu.devSphere.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 *
 */
@TableName(value ="dev_sphere_user_room_relate")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRoomRelate implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 房间 id
     */
    private Long roomId;

    /**
     * 最新已读 id
     */
    private Long latestReadMsgId;

    /**
     * 消息起始可见ID (用于删除会话时的逻辑清空)
     * 查询消息时，只查 ID > minMsgId 的记录
     */
    private Long minMsgId;

    /**
     * 是否删除/隐藏会话 (0: 正常显示, 1: 删除/隐藏)
     */
    private Integer isDeleted;
    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}