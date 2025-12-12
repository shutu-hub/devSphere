package com.shutu.util;

import org.apache.commons.lang3.StringUtils;

/**
 * SQL 工具
 */
public class SqlUtils {

    private SqlUtils() {
    }
    /**
     * 校验排序字段是否合法（防止 SQL 注入）
     *
     * @param sortField 排序字段
     * @return boolean
     */
    public static boolean validSortField(String sortField) {
        if (StringUtils.isBlank(sortField)) {
            return false;
        }
        return !StringUtils.containsAny(sortField, "=", "(", ")", " ");
    }
}
