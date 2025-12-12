package com.shutu.model.vo.friend;

import lombok.Data;

import java.util.List;

/**
 * 好友列表
 */
@Data
public class FriendContentVo {
    /**
     *类型
     */
    private Integer type;

    /**
     * 类型名称
     */
    private String typeName;

    private List<FriendVo> content;

}
