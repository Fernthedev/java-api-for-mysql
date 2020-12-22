/*
 Copyright (c) Petr Panteleyev. All rights reserved.
 Licensed under the BSD license. See LICENSE file in the project root for full license information.
 */
package org.panteleyev.mysqlapi;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;
import java.util.function.BiFunction;

public interface DataReaders {
    BiFunction<Object, Class<?>, Object> OBJECT_READER = (Object val, Class<?> field) -> val;

    BiFunction<Object, Class<?>, Boolean> BOOL_READER = (Object val, Class<?> field) -> {

        if (val instanceof Number) {
            val = ((Number) val).intValue() == 1;
        } else {
            val = Boolean.parseBoolean(val.toString());
        }

        return (Boolean) val;
    };

    BiFunction<Object, Class<?>, BigDecimal> BIG_DECIMAL_READER = (Object val, Class<?> field) -> BigDecimal.valueOf((Double) val);

    BiFunction<Object, Class<?>, Integer> INT_READER = (Object val, Class<?> field) -> (Integer) val;

    BiFunction<Object, Class<?>, Long> LONG_READER = (Object val, Class<?> field) -> (Long) val;

    BiFunction<Object, Class<?>, Date> DATE_READER = (Object val, Class<?> field) -> val == null ? null : new Date((long) val);

    BiFunction<Object, Class<?>, LocalDate> LOCAL_DATE_READER = (Object val, Class<?> field) -> val == null ? null : LocalDate.ofEpochDay((long) val);

    BiFunction<Object, Class<?>, byte[]> BYTE_ARRAY_READER = (Object val, Class<?> field) -> (byte[]) val;

    BiFunction<Object, Class<?>, UUID> UUID_STRING_READER = (Object val, Class<?> field) -> {
        String uuid = (String) val;
        return uuid == null ? null : UUID.fromString(uuid);
    };
}
