package org.panteleyev.mysqlapi;

/*
 * Copyright (c) Petr Panteleyev. All rights reserved.
 * Licensed under the BSD license. See LICENSE file in the project root for full license information.
 */

import org.panteleyev.mysqlapi.annotations.Column;
import org.panteleyev.mysqlapi.annotations.ForeignKey;
import org.panteleyev.mysqlapi.annotations.Index;
import org.panteleyev.mysqlapi.annotations.PrimaryKey;
import org.panteleyev.mysqlapi.annotations.ReferenceOption;
import org.panteleyev.mysqlapi.annotations.Table;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import static org.panteleyev.mysqlapi.DataReaders.BIG_DECIMAL_READER;
import static org.panteleyev.mysqlapi.DataReaders.BOOL_READER;
import static org.panteleyev.mysqlapi.DataReaders.BYTE_ARRAY_READER;
import static org.panteleyev.mysqlapi.DataReaders.DATE_READER;
import static org.panteleyev.mysqlapi.DataReaders.INT_READER;
import static org.panteleyev.mysqlapi.DataReaders.LOCAL_DATE_READER;
import static org.panteleyev.mysqlapi.DataReaders.LONG_READER;
import static org.panteleyev.mysqlapi.DataReaders.OBJECT_READER;
import static org.panteleyev.mysqlapi.DataReaders.UUID_STRING_READER;
import static org.panteleyev.mysqlapi.DataTypes.BAD_FIELD_TYPE;
import static org.panteleyev.mysqlapi.DataTypes.FIELD_NOT_ANNOTATED;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_BIG_DECIMAL;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_BOOL;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_BOOLEAN;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_BYTE_ARRAY;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_DATE;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_ENUM;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_INT;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_INTEGER;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_LOCAL_DATE;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_LONG;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_LONG_PRIM;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_STRING;
import static org.panteleyev.mysqlapi.DataTypes.TYPE_UUID;

public class MySqlProxy {
    private static final Map<String, BiFunction<Object, Class<?>, ?>> RESULT_SET_READERS = new HashMap<>();

    static {
        RESULT_SET_READERS.put(TYPE_STRING, OBJECT_READER);
        RESULT_SET_READERS.put(TYPE_INTEGER, OBJECT_READER);
        RESULT_SET_READERS.put(TYPE_INT, INT_READER);
        RESULT_SET_READERS.put(TYPE_LONG, OBJECT_READER);
        RESULT_SET_READERS.put(TYPE_LONG_PRIM, LONG_READER);
        RESULT_SET_READERS.put(TYPE_BOOL, BOOL_READER);
        RESULT_SET_READERS.put(TYPE_BOOLEAN, OBJECT_READER);
        RESULT_SET_READERS.put(TYPE_BIG_DECIMAL, BIG_DECIMAL_READER);
        RESULT_SET_READERS.put(TYPE_DATE, DATE_READER);
        RESULT_SET_READERS.put(TYPE_LOCAL_DATE, LOCAL_DATE_READER);
        RESULT_SET_READERS.put(TYPE_BYTE_ARRAY, BYTE_ARRAY_READER);
        RESULT_SET_READERS.put(TYPE_UUID, UUID_STRING_READER);
    }

    public Map<String, BiFunction<Object, Class<?>, ?>> getReaderMap() {
        return RESULT_SET_READERS;
    }

    public Object getFieldValue(String fieldName, Class typeClass, ResultSet set) throws SQLException {
        Object value = set.getObject(fieldName);

        return getFieldValue(typeClass, value);

    }

    public Object getFieldValue(Class typeClass, Object value) {
        if (typeClass.isEnum()) {
            return value == null ? null : Enum.valueOf(typeClass, (String) value);
        }

        BiFunction<Object, Class<?>, ?> reader = getReaderMap().get(typeClass.getTypeName());
        if (reader == null) {
            throw new IllegalStateException(BAD_FIELD_TYPE + typeClass.getTypeName());
        }

        return reader.apply(value, typeClass);
    }

    public String buildForeignKey(Column column, ForeignKey key) {
        Objects.requireNonNull(key);

        Class<?> parentTableClass = key.table();
        if (!parentTableClass.isAnnotationPresent(Table.class)) {
            throw new IllegalStateException("Foreign key references not annotated table");
        }

        String parentTableName = parentTableClass.getAnnotation(Table.class).value();
        String parentFieldName = key.column();

        StringBuilder fk = new StringBuilder();

        fk.append("FOREIGN KEY (")
            .append(column.value())
            .append(") ")
            .append("REFERENCES ")
            .append(parentTableName)
            .append("(")
            .append(parentFieldName)
            .append(")");

        if (key.onUpdate() != ReferenceOption.NONE) {
            fk.append(" ON UPDATE ").append(key.onUpdate().toString());
        }

        if (key.onDelete() != ReferenceOption.NONE) {
            fk.append(" ON DELETE ").append(key.onDelete().toString());
        }

        return fk.toString();
    }

    public String getColumnString(Column column, PrimaryKey primaryKey, ForeignKey foreignKey, String typeName,
                           List<String> constraints)
    {
        StringBuilder b = new StringBuilder();

        switch (typeName) {
            case TYPE_STRING:
                if (column.isJson()) {
                    b.append("JSON");
                } else {
                    b.append("VARCHAR(")
                        .append(column.length())
                        .append(")");
                }
                break;
            case TYPE_ENUM:
                b.append("VARCHAR(")
                    .append(column.length())
                    .append(")");
                break;
            case TYPE_BOOL:
            case TYPE_BOOLEAN:
                b.append("BOOLEAN");
                break;
            case TYPE_INTEGER:
            case TYPE_INT:
                b.append("INTEGER");
                break;
            case TYPE_LONG:
            case TYPE_LONG_PRIM:
            case TYPE_DATE:
            case TYPE_LOCAL_DATE:
                b.append("BIGINT");
                break;
            case TYPE_BIG_DECIMAL:
                b.append("DECIMAL(")
                    .append(column.precision())
                    .append(",")
                    .append(column.scale())
                    .append(")");
                break;
            case TYPE_BYTE_ARRAY:
                b.append("VARBINARY(")
                    .append(column.length())
                    .append(")");
                break;
            case TYPE_UUID:
                if (column.storeUuidAsBinary()) {
                    b.append("BINARY(16)");
                } else {
                    b.append("VARCHAR(36)");
                }
                break;
            default:
                throw new IllegalStateException(BAD_FIELD_TYPE + typeName);
        }

        if (primaryKey != null) {
            b.append(" PRIMARY KEY");
        }

        if (!column.nullable()) {
            b.append(" NOT NULL");
        }

        if (column.unique()) {
            b.append(" UNIQUE");
        }

        if (foreignKey != null) {
            constraints.add(buildForeignKey(column, foreignKey));
        }

        return b.toString();
    }

    public void truncate(Connection connection, List<Class<? extends TableRecord>> tables) {
        try (Statement statement = connection.createStatement()) {
            for (Class<? extends TableRecord> t : tables) {
                statement.execute("TRUNCATE TABLE " + TableRecord.getTableName(t));
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void setFieldData(PreparedStatement st, int index, Object value, String typeName) throws SQLException {
        switch (typeName) {
            case TYPE_STRING:
                if (value == null) {
                    st.setNull(index, Types.VARCHAR);
                } else {
                    st.setString(index, (String) value);
                }
                break;
            case TYPE_UUID:
                if (value == null) {
                    st.setNull(index, Types.VARCHAR);
                } else {
                    st.setString(index, value.toString());
                }
                break;
            case TYPE_BOOL:
            case TYPE_BOOLEAN:
                if (value == null) {
                    st.setNull(index, Types.BOOLEAN);
                } else {
                    st.setBoolean(index, (Boolean) value);
                }
                break;
            case TYPE_INTEGER:
            case TYPE_INT:
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setInt(index, (Integer) value);
                }
                break;
            case TYPE_LONG:
            case TYPE_LONG_PRIM:
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setLong(index, (Long) value);
                }
                break;
            case TYPE_DATE:
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setLong(index, ((Date) value).getTime());
                }
                break;
            case TYPE_LOCAL_DATE:
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setLong(index, ((LocalDate) value).toEpochDay());
                }
                break;
            case TYPE_BIG_DECIMAL:
                if (value == null) {
                    st.setNull(index, Types.DECIMAL);
                } else {
                    st.setBigDecimal(index, (BigDecimal) value);
                }
                break;
            case TYPE_ENUM:
                if (value == null) {
                    st.setNull(index, Types.VARCHAR);
                } else {
                    st.setString(index, ((Enum) value).name());
                }
                break;
            case TYPE_BYTE_ARRAY:
                if (value == null) {
                    st.setNull(index, Types.VARBINARY);
                } else {
                    st.setBytes(index, (byte[]) value);
                }
                break;
            default:
                throw new IllegalStateException(BAD_FIELD_TYPE + typeName);
        }
    }

    public String getSelectColumnString(Field field) {
        Column column = field.getAnnotation(Column.class);
        Objects.requireNonNull(column, FIELD_NOT_ANNOTATED + field.getName());

        if (field.getType().getTypeName().equals(TYPE_UUID) && column.storeUuidAsBinary()) {
            return "BIN_TO_UUID(" + column.value() + ") AS " + column.value();
        } else {
            return column.value();
        }
    }

    public String getInsertColumnPattern(Field field) {
        Column column = field.getAnnotation(Column.class);
        Objects.requireNonNull(column, FIELD_NOT_ANNOTATED + field.getName());

        if (field.getType().getTypeName().equals(TYPE_UUID) && column.storeUuidAsBinary()) {
            return "UUID_TO_BIN(?)";
        } else {
            return "?";
        }
    }

    public String getUpdateColumnPattern(Field field) {
        return getInsertColumnPattern(field);
    }

    public String getWhereColumnString(Field field) {
        Column column = field.getAnnotation(Column.class);
        Objects.requireNonNull(column, FIELD_NOT_ANNOTATED + field.getName());

        if (field.getType().getTypeName().equals(TYPE_UUID) && column.storeUuidAsBinary()) {
            return "BIN_TO_UUID(" + column.value() + ")";
        } else {
            return column.value();
        }
    }

    public String buildIndex(Table table, Field field) {
        Column column = field.getAnnotation(Column.class);
        Index index = field.getAnnotation(Index.class);

        StringBuilder b = new StringBuilder("CREATE ");
        if (index.unique()) {
            b.append("UNIQUE ");
        }

        b.append("INDEX ")
            .append(index.value())
            .append(" ON ")
            .append(table.value())
            .append(" (")
            .append(column.value())
            .append(")");

        return b.toString();
    }

    public void deleteAll(Connection connection, Class<? extends TableRecord> table) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + TableRecord.getTableName(table));
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
