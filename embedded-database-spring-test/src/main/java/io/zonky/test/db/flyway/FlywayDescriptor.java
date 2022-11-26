/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.springframework.util.ReflectionUtils.FieldFilter;
import static org.springframework.util.ReflectionUtils.doWithFields;
import static org.springframework.util.ReflectionUtils.getField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;

public class FlywayDescriptor {

    private static final Set<String> BASIC_FIELDS = ImmutableSet.of(
            "locations", "schemaNames", "table",
            "sqlMigrationPrefix", "repeatableSqlMigrationPrefix",
            "sqlMigrationSeparator", "sqlMigrationSuffixes",
            "ignoreMissingMigrations", "ignoreFutureMigrations",
            "validateOnMigrate"
    );

    private static final Set<String> EXCLUDED_FIELDS = ImmutableSet.of(
            "cleanDisabled", "dbConnectionInfoPrinted", "classScanner", "pluginRegister"
    );

    private static final Set<Class<?>> EXCLUDED_TYPES = ImmutableSet.of(
            DataSource.class, ClassLoader.class, OutputStream.class
    );

    private static final FieldFilter OTHER_FIELDS =
            field -> !Modifier.isStatic(field.getModifiers())
                    && !BASIC_FIELDS.contains(field.getName())
                    && !EXCLUDED_FIELDS.contains(field.getName())
                    && !EXCLUDED_TYPES.contains(field.getType());

    private final List<String> locations;
    private final List<String> schemas;
    private final String table;
    private final String sqlMigrationPrefix;
    private final String repeatableSqlMigrationPrefix;
    private final String sqlMigrationSeparator;
    private final List<String> sqlMigrationSuffixes;
    private final boolean ignoreMissingMigrations;
    private final boolean ignoreFutureMigrations;
    private final boolean validateOnMigrate;
    private final Map<Field, Object> otherFields;

    private FlywayDescriptor(FlywayWrapper wrapper) {
        this.locations = ImmutableList.copyOf(wrapper.getLocations());
        this.schemas = ImmutableList.copyOf(wrapper.getSchemas());
        this.table = wrapper.getTable();
        this.sqlMigrationPrefix = wrapper.getSqlMigrationPrefix();
        this.repeatableSqlMigrationPrefix = wrapper.getRepeatableSqlMigrationPrefix();
        this.sqlMigrationSeparator = wrapper.getSqlMigrationSeparator();
        this.sqlMigrationSuffixes = ImmutableList.copyOf(wrapper.getSqlMigrationSuffixes());
        this.ignoreMissingMigrations = wrapper.isIgnoreMissingMigrations();
        this.ignoreFutureMigrations = wrapper.isIgnoreFutureMigrations();
        this.validateOnMigrate = wrapper.isValidateOnMigrate();

        Map<Field, Object> otherFields = new HashMap<>();
        Object config = wrapper.getConfig();

        doWithFields(config.getClass(), field -> {
            makeAccessible(field);
            Object value = getField(field, config);

            if (value != null) {
                if (Collection.class.isAssignableFrom(field.getType())) {
                    value = Lists.newArrayList((Collection<?>) value);
                } else if (Map.class.isAssignableFrom(field.getType())) {
                    value = Maps.newHashMap((Map<?, ?>) value);
                } else if (field.getType().isArray()) {
                    value = Lists.newArrayList((Object[]) value);
                }
            }

            otherFields.put(field, value);
        }, OTHER_FIELDS);

        this.otherFields = otherFields;
    }

    public static FlywayDescriptor from(FlywayWrapper wrapper) {
        return new FlywayDescriptor(wrapper);
    }

    public void applyTo(FlywayWrapper wrapper) {
        Object config = wrapper.getConfig();

        wrapper.setLocations(locations);
        wrapper.setSchemas(schemas);
        wrapper.setTable(table);
        wrapper.setSqlMigrationPrefix(sqlMigrationPrefix);
        wrapper.setRepeatableSqlMigrationPrefix(repeatableSqlMigrationPrefix);
        wrapper.setSqlMigrationSeparator(sqlMigrationSeparator);
        wrapper.setSqlMigrationSuffixes(sqlMigrationSuffixes);
        wrapper.setIgnoreMissingMigrations(ignoreMissingMigrations);
        wrapper.setIgnoreFutureMigrations(ignoreFutureMigrations);
        wrapper.setValidateOnMigrate(validateOnMigrate);

        otherFields.forEach((field, value) -> {
            makeAccessible(field);

            if (value == null) {
                setField(field, config, value);
            } else if (Collection.class.isAssignableFrom(field.getType())) {
                setCollection(field, config, (Collection<?>) value);
            } else if (Map.class.isAssignableFrom(field.getType())) {
                setMap(field, config, (Map<?, ?>) value);
            } else if (field.getType().isArray()) {
                setArray(field, config, (List<?>) value);
            } else {
                setField(field, config, value);
            }
        });
    }

    public List<String> getLocations() {
        return locations;
    }

    public List<String> getSchemas() {
        return schemas;
    }

    public String getTable() {
        return table;
    }

    public String getSqlMigrationPrefix() {
        return sqlMigrationPrefix;
    }

    public String getRepeatableSqlMigrationPrefix() {
        return repeatableSqlMigrationPrefix;
    }

    public String getSqlMigrationSeparator() {
        return sqlMigrationSeparator;
    }

    public List<String> getSqlMigrationSuffixes() {
        return sqlMigrationSuffixes;
    }

    public boolean isIgnoreMissingMigrations() {
        return ignoreMissingMigrations;
    }

    public boolean isIgnoreFutureMigrations() {
        return ignoreFutureMigrations;
    }

    public boolean isValidateOnMigrate() {
        return validateOnMigrate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlywayDescriptor that = (FlywayDescriptor) o;
        return ignoreMissingMigrations == that.ignoreMissingMigrations
                && ignoreFutureMigrations == that.ignoreFutureMigrations
                && validateOnMigrate == that.validateOnMigrate
                && Objects.equals(locations, that.locations)
                && Objects.equals(schemas, that.schemas)
                && Objects.equals(table, that.table)
                && Objects.equals(sqlMigrationPrefix, that.sqlMigrationPrefix)
                && Objects.equals(repeatableSqlMigrationPrefix, that.repeatableSqlMigrationPrefix)
                && Objects.equals(sqlMigrationSeparator, that.sqlMigrationSeparator)
                && Objects.equals(sqlMigrationSuffixes, that.sqlMigrationSuffixes)
                && Objects.equals(otherFields, that.otherFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locations, schemas, table,
                sqlMigrationPrefix, repeatableSqlMigrationPrefix,
                sqlMigrationSeparator, sqlMigrationSuffixes,
                ignoreMissingMigrations, ignoreFutureMigrations,
                validateOnMigrate, otherFields);
    }

    private static void setCollection(Field field, Object target, Collection<?> value) {
        Collection collection = (Collection) getField(field, target);
        if (collection != null) {
            collection.clear();
            collection.addAll(value);
        } else {
            setField(field, target, value);
        }
    }

    private static void setMap(Field field, Object target, Map<?, ?> value) {
        Map map = (Map) getField(field, target);
        if (map != null) {
            map.clear();
            map.putAll(value);
        } else {
            setField(field, target, value);
        }
    }

    private static void setArray(Field field, Object config, List<?> value) {
        Class<?> componentType = field.getType().getComponentType();
        Object[] array = value.toArray((Object[]) Array.newInstance(componentType, 0));
        setField(field, config, array);
    }
}
