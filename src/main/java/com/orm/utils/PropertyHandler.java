package com.orm.utils;

/**
 * @Auther: luoqw
 * @Date: 2018/6/11 16:53
 * @Description:
 */
public interface PropertyHandler {

    boolean match(Class<?> var1, Object var2);

    Object apply(Class<?> var1, Object var2);
}
