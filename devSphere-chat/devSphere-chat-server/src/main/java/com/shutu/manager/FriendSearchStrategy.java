package com.shutu.manager;

import com.shutu.model.vo.friend.AddFriendVo;


public interface FriendSearchStrategy {

    /**
     * 搜索
     * @param id id
     * @return {@link AddFriendVo}
     */
    AddFriendVo search(String id);

    /**
     * 判断该策略是否支持查询
     * @param id id
     * @return boolean
     */
    boolean supports(String id);
}
