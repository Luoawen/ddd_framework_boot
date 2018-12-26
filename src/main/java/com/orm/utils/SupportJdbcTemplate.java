package com.orm.utils;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @Auther: luoqw
 * @Date: 2018/6/11 16:55
 * @Description:
 */
@Component
public class SupportJdbcTemplate {

    @Resource
    JdbcTemplate jdbcTemplate;

    public SupportJdbcTemplate() {
    }

    public <T> T queryForBean(String sql, final Class<T> beanType) {
        return this.jdbcTemplate().query(sql, rs -> rs.next() ? (new BeanProcessor()).toBean(rs, beanType) : null);
    }

    public JdbcOperations jdbcTemplate() {
        return this.jdbcTemplate;
    }

    public <T> T queryForBean(String sql, final Class<T> beanType, Object... args) {
        return this.jdbcTemplate().query(sql, args, rs -> rs.next() ? (new BeanProcessor()).toBean(rs, beanType) : null);
    }

    public <T> T queryForBean(String sql, final Class<T> beanType, Object[] args, int[] argTypes) {
        return this.jdbcTemplate().query(sql, args, argTypes, rs -> rs.next() ? (new BeanProcessor()).toBean(rs, beanType) : null);
    }

    public <T> List<T> queryForBeanList(String sql, final Class<T> beanType) {
        return this.jdbcTemplate().query(sql, (rs, rowNum) -> (new BeanProcessor()).toBean(rs, beanType));
    }

    public <T> List<T> queryForBeanList(String sql, final Class<T> beanType, Object... args) {
        return this.jdbcTemplate().query(sql, args, (rs, rowNum) -> (new BeanProcessor()).toBean(rs, beanType));
    }

    public <T> List<T> queryForBeanList(String sql, final Class<T> beanType, Object[] args, int[] argTypes) {
        return this.jdbcTemplate().query(sql, args, argTypes, (rs, rowNum) -> (new BeanProcessor()).toBean(rs, beanType));
    }

}
