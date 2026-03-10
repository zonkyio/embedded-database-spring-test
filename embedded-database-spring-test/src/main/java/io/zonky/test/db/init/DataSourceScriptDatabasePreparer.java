/*
 * Copyright 2025 the original author or authors.
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

package io.zonky.test.db.init;

import com.cedarsoftware.util.DeepEquals;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.util.ReflectionUtils;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;

import org.springframework.util.ReflectionUtils.FieldFilter;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.util.ReflectionUtils.makeAccessible;

public class DataSourceScriptDatabasePreparer implements DatabasePreparer {

    private static final Set<String> EXCLUDED_FIELDS = new HashSet<>(Arrays.asList("dataSource", "resourceLoader"));

    private static final FieldFilter FIELD_FILTER =
            field -> !Modifier.isStatic(field.getModifiers()) && !EXCLUDED_FIELDS.contains(field.getName());

    private final DataSourceScriptDatabaseInitializer initializer;
    private final ThreadLocalDataSource threadLocalDataSource;

    public DataSourceScriptDatabasePreparer(DataSourceScriptDatabaseInitializer initializer) {
        this.initializer = initializer;
        this.threadLocalDataSource = new ThreadLocalDataSource();
        ReflectionUtils.setField(initializer, "dataSource", threadLocalDataSource);
    }

    @Override
    public long estimatedDuration() {
        return 10;
    }

    @Override
    public void prepare(DataSource dataSource) {
        threadLocalDataSource.set(dataSource);
        try {
            initializer.initializeDatabase();
        } finally {
            threadLocalDataSource.clear();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataSourceScriptDatabasePreparer that = (DataSourceScriptDatabasePreparer) o;
        if (initializer.getClass() != that.initializer.getClass()) return false;
        AtomicBoolean equal = new AtomicBoolean(true);
        org.springframework.util.ReflectionUtils.doWithFields(initializer.getClass(),
                field -> {
                    if (!equal.get()) return;
                    makeAccessible(field);
                    if (!DeepEquals.deepEquals(field.get(initializer), field.get(that.initializer))) {
                        equal.set(false);
                    }
                },
                FIELD_FILTER);
        return equal.get();
    }

    @Override
    public int hashCode() {
        AtomicInteger result = new AtomicInteger(initializer.getClass().hashCode());
        org.springframework.util.ReflectionUtils.doWithFields(initializer.getClass(),
                field -> {
                    makeAccessible(field);
                    result.set(31 * result.get() + DeepEquals.deepHashCode(field.get(initializer)));
                },
                FIELD_FILTER);
        return result.get();
    }
}
