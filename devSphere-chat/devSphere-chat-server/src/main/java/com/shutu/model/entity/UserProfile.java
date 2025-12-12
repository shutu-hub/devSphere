package com.shutu.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user_profile")
public class UserProfile {
    @TableId
    private Long userId;
    private String displayName;
    private String avatar;
    private String bio;
    private Integer gender;
    private String location;
    private Date updatedAt;
    private Date createdAt;
}
