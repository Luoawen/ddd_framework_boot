package com.orm.utils;

import org.springframework.util.StringUtils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * @Auther: luoqw
 * @Date: 2018/6/11 16:51
 * @Description:
 */
public class BeanProcessor {
    protected static final int PROPERTY_NOT_FOUND = -1;
    private static final Map<Class<?>, Object> primitiveDefaults = new HashMap();
    private static final ServiceLoader<ColumnHandler> columnHandlers = ServiceLoader.load(ColumnHandler.class);
    private static final ServiceLoader<PropertyHandler> propertyHandlers = ServiceLoader.load(PropertyHandler.class);
    private final Map<String, String> columnToPropertyOverrides;

    static {
        primitiveDefaults.put(Integer.TYPE, 0);
        primitiveDefaults.put(Short.TYPE, Short.valueOf((short)0));
        primitiveDefaults.put(Byte.TYPE, 0);
        primitiveDefaults.put(Float.TYPE, 0.0F);
        primitiveDefaults.put(Double.TYPE, 0.0D);
        primitiveDefaults.put(Long.TYPE, 0L);
        primitiveDefaults.put(Boolean.TYPE, Boolean.FALSE);
        primitiveDefaults.put(Character.TYPE, '\u0000');
    }

    public BeanProcessor() {
        this(new HashMap());
    }

    public BeanProcessor(Map<String, String> columnToPropertyOverrides) {
        if (columnToPropertyOverrides == null) {
            throw new IllegalArgumentException("columnToPropertyOverrides map cannot be null");
        } else {
            this.columnToPropertyOverrides = columnToPropertyOverrides;
        }
    }

    public <T> T toBean(ResultSet rs, Class<? extends T> type) throws SQLException {
        T bean = this.newInstance(type);
        return this.populateBean(rs, bean);
    }

    public <T> List<T> toBeanList(ResultSet rs, Class<? extends T> type) throws SQLException {
        List<T> results = new ArrayList();
        if (!rs.next()) {
            return results;
        } else {
            PropertyDescriptor[] props = this.propertyDescriptors(type);
            ResultSetMetaData rsmd = rs.getMetaData();
            this.createColumnToPropertyOverridesByAnnotation(type);
            int[] columnToProperty = this.mapColumnsToProperties(rsmd, props);

            do {
                results.add(this.createBean(rs, type, props, columnToProperty));
            } while(rs.next());

            return results;
        }
    }

    private <T> T createBean(ResultSet rs, Class<T> type, PropertyDescriptor[] props, int[] columnToProperty) throws SQLException {
        T bean = this.newInstance(type);
        return this.populateBean(rs, bean, props, columnToProperty);
    }

    public <T> T populateBean(ResultSet rs, T bean) throws SQLException {
        PropertyDescriptor[] props = this.propertyDescriptors(bean.getClass());
        ResultSetMetaData rsmd = rs.getMetaData();
        this.createColumnToPropertyOverridesByAnnotation(bean);
        int[] columnToProperty = this.mapColumnsToProperties(rsmd, props);
        return this.populateBean(rs, bean, props, columnToProperty);
    }

    private <T> T populateBean(ResultSet rs, T bean, PropertyDescriptor[] props, int[] columnToProperty) throws SQLException {
        for(int i = 1; i < columnToProperty.length; ++i) {
            if (columnToProperty[i] != -1) {
                PropertyDescriptor prop = props[columnToProperty[i]];
                Class<?> propType = prop.getPropertyType();
                Object value = null;
                if (propType != null) {
                    value = this.processColumn(rs, i, propType);
                    if (value == null && propType.isPrimitive()) {
                        value = primitiveDefaults.get(propType);
                    }
                }

                this.callFiledSetter(bean, prop, value);
            }
        }

        return bean;
    }

    private void callSetter(Object target, PropertyDescriptor prop, Object value) throws SQLException {
        Method setter = this.getWriteMethod(target, prop, value);
        if (setter != null && setter.getParameterTypes().length == 1) {
            try {
                Class<?> firstParam = setter.getParameterTypes()[0];
                Iterator var7 = propertyHandlers.iterator();

                while(var7.hasNext()) {
                    PropertyHandler handler = (PropertyHandler)var7.next();
                    if (handler.match(firstParam, value)) {
                        value = handler.apply(firstParam, value);
                        break;
                    }
                }
                if (this.isCompatibleType(value, firstParam)) {
                    setter.invoke(target, value);
                } else {
                    throw new SQLException("Cannot set " + prop.getName() + ": incompatible types, cannot convert " + value.getClass().getName() + " to " + firstParam.getName());
                }
            } catch (IllegalArgumentException var8) {
                throw new SQLException("Cannot set " + prop.getName() + ": " + var8.getMessage());
            } catch (IllegalAccessException var9) {
                throw new SQLException("Cannot set " + prop.getName() + ": " + var9.getMessage());
            } catch (InvocationTargetException var10) {
                throw new SQLException("Cannot set " + prop.getName() + ": " + var10.getMessage());
            }
        }
    }

    private void callFiledSetter(Object target, PropertyDescriptor prop, Object value) throws SQLException {
        Field field = null;

        try {
            field = target.getClass().getDeclaredField(prop.getName());
            if (field == null) {
                return;
            }

            field.setAccessible(true);
        } catch (SecurityException | NoSuchFieldException var10) {
            throw new SQLException("NoSuchFieldException " + prop.getName() + ": " + var10.getMessage());
        }

        try {
            Class<?> firstParam = field.getType();
            Iterator var7 = propertyHandlers.iterator();

            while(var7.hasNext()) {
                PropertyHandler handler = (PropertyHandler)var7.next();
                if (handler.match(firstParam, value)) {
                    value = handler.apply(firstParam, value);
                    break;
                }
            }

            if (this.isCompatibleType(value, firstParam)) {
                field.set(target, value);
            } else {
                throw new SQLException("Cannot set " + prop.getName() + ": incompatible types, cannot convert " + value.getClass().getName() + " to " + firstParam.getName());
            }
        } catch (IllegalArgumentException var8) {
            throw new SQLException("Cannot set " + prop.getName() + ": " + var8.getMessage());
        } catch (IllegalAccessException var9) {
            throw new SQLException("Cannot set " + prop.getName() + ": " + var9.getMessage());
        }
    }

    private boolean isCompatibleType(Object value, Class<?> type) {
        return value == null || type.isInstance(value) || this.matchesPrimitive(type, value.getClass());
    }

    private boolean matchesPrimitive(Class<?> targetType, Class<?> valueType) {
        if (!targetType.isPrimitive()) {
            return false;
        } else {
            try {
                Field typeField = valueType.getField("TYPE");
                Object primitiveValueType = typeField.get(valueType);
                if (targetType == primitiveValueType) {
                    return true;
                }
            } catch (NoSuchFieldException var5) {
                ;
            } catch (IllegalAccessException var6) {
                ;
            }

            return false;
        }
    }

    protected Method getWriteMethod(Object target, PropertyDescriptor prop, Object value) {
        Method method = prop.getWriteMethod();
        return method;
    }

    protected <T> T newInstance(Class<T> c) throws SQLException {
        try {
            return c.newInstance();
        } catch (InstantiationException var3) {
            throw new SQLException("Cannot create " + c.getName() + ": " + var3.getMessage());
        } catch (IllegalAccessException var4) {
            throw new SQLException("Cannot create " + c.getName() + ": " + var4.getMessage());
        }
    }

    private PropertyDescriptor[] propertyDescriptors(Class<?> c) throws SQLException {
        BeanInfo beanInfo = null;

        try {
            beanInfo = Introspector.getBeanInfo(c);
        } catch (IntrospectionException var4) {
            throw new SQLException("Bean introspection failed: " + var4.getMessage());
        }

        return beanInfo.getPropertyDescriptors();
    }

    protected int[] mapColumnsToProperties(ResultSetMetaData rsmd, PropertyDescriptor[] props) throws SQLException {
        int cols = rsmd.getColumnCount();
        int[] columnToProperty = new int[cols + 1];
        Arrays.fill(columnToProperty, -1);

        for(int col = 1; col <= cols; ++col) {
            String columnName = rsmd.getColumnLabel(col);
            if (columnName == null || columnName.length() == 0) {
                columnName = rsmd.getColumnName(col);
            }

            String propertyName = (String)this.columnToPropertyOverrides.get(columnName);
            if (propertyName == null) {
                propertyName = columnName;
            }

            for(int i = 0; i < props.length; ++i) {
                if (propertyName.equalsIgnoreCase(props[i].getName())) {
                    columnToProperty[col] = i;
                    break;
                }
            }
        }

        return columnToProperty;
    }

    protected Object processColumn(ResultSet rs, int index, Class<?> propType) throws SQLException {
        Object retval = rs.getObject(index);
        if (!propType.isPrimitive() && retval == null) {
            return null;
        } else {
            Iterator var6 = columnHandlers.iterator();

            while(var6.hasNext()) {
                ColumnHandler handler = (ColumnHandler)var6.next();
                if (handler.match(propType)) {
                    retval = handler.apply(rs, index);
                    break;
                }
            }

            return retval;
        }
    }

    private <T> void createColumnToPropertyOverridesByAnnotation(T bean) {
        this.columnToPropertyOverrides.clear();
        Field[] fields = bean.getClass().getDeclaredFields();
        Field[] var6 = fields;
        int var5 = fields.length;

        for(int var4 = 0; var4 < var5; ++var4) {
            Field field = var6[var4];
            if (field.isAnnotationPresent(ColumnAlias.class)) {
                ColumnAlias colAlias = (ColumnAlias)field.getAnnotation(ColumnAlias.class);
                if (!StringUtils.isEmpty(colAlias.value())) {
                    this.columnToPropertyOverrides.put(colAlias.value(), field.getName());
                }
            }
        }

    }

}
