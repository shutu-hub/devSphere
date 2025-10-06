package com.shutu.devSphere.datasource;

import com.shutu.devSphere.model.vo.friend.FriendContentVo;

import java.util.List;

/**
 * 数据源接口（新接入数据源必须实现 ）
 */
public interface DataSource {
    /**
     * 做搜索
     *
     * @param ids id列表
     */
    FriendContentVo doSearch(List<Long> ids);
}
