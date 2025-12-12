package com.shutu.model.vo.moment;

import lombok.Data;

@Data
public class UserVo {
    private Long userId; // 修改: uid -> userId
    private String username;
    private String avatar;
    private String displayName;
}