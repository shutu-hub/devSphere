package com.shutu.manager;

import com.shutu.datasource.DataSource;
import com.shutu.datasource.FriendDataSource;
import com.shutu.datasource.GroupDataSource;
import com.shutu.model.enums.chat.FriendSearchTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据源注册表
 */
@Component
@RequiredArgsConstructor
public class DataSourceRegistry {

    private Map<Integer, DataSource> typeDataSourceMap;
    private final FriendDataSource friendDataSource;
    private final GroupDataSource groupDataSource;


    @PostConstruct
    public void doInit() {
        typeDataSourceMap = new HashMap() {{
            put(FriendSearchTypeEnum.GROUP.getType(), groupDataSource);
            put(FriendSearchTypeEnum.FRIEND.getType(), friendDataSource);
        }};
    }


    public DataSource getDataSourceByType(Integer type) {
        if (typeDataSourceMap==null){
            return null;
        }
        return typeDataSourceMap.get(type);
    }
}
