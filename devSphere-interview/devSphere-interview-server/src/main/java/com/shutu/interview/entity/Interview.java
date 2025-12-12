package com.shutu.interview.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shutu.commons.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试记录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dev_sphere_interviews")
public class Interview extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID (关联 shutu-auth)
     */
    private Long userId;

    /**
     * 岗位ID
     */
    private Long jobId;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 状态: 0:准备中, 1:进行中, 2:已完成, 3:已终止
     */
    private Integer status;

    /**
     * 总分
     */
    private BigDecimal totalScore;

    /**
     * 面试总结
     */
    /**
     * 面试总结
     */
    private String summary;

    /**
     * 简历地址
     */
    private String resumeUrl;

    /**
     * 面试方向/分类 (如: Java, Frontend)
     */
    private String category;
}
