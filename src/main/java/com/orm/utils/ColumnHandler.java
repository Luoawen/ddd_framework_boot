package com.orm.utils;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @Auther: luoqw
 * @Date: 2018/6/11 16:52
 * @Description:
 */
public interface ColumnHandler {
    boolean match(Class<?> var1);

    Object apply(ResultSet var1, int var2) throws SQLException;
}
