package com.shutu.manager;

import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.datasource.DataSource;
import com.shutu.model.vo.friend.FriendContentVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 好友列表门面
 */
@Component
@Slf4j
public class FriendSearchFacade {

    @Resource
    private DataSourceRegistry dataSourceRegistry;

    public FriendContentVo searchAll(Integer type, List<Long> ids) {
        if (type == null) {
            throw new CommonException("未指定查询类型", ErrorCode.PARAMS_GET_ERROR);
        } else {
            DataSource dataSource = dataSourceRegistry.getDataSourceByType(type);
            return dataSource.doSearch(ids);
        }
    }
}
