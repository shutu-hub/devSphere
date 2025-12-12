package com.shutu.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("moments_post")
public class MomentPost {
    @TableId
    private Long id;
    private Long userId;
    private String content;
    private String imageUrls; // JSON string
    private Integer likeCount;
    private Integer commentCount;
    private Integer visibility;
    private Date createdAt;
    private Date updatedAt;
}
