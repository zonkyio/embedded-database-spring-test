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

package io.zonky.test.db.liquibase;

import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.util.ReflectionUtils;
import liquibase.exception.ChangeLogParseException;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

public class LiquibaseDatabasePreparer implements DatabasePreparer {

    private static final Logger logger = LoggerFactory.getLogger(LiquibaseDatabasePreparer.class);

    private final LiquibaseDescriptor descriptor;

    private volatile Long estimatedDuration;

    public LiquibaseDatabasePreparer(LiquibaseDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public long estimatedDuration() {
        if (estimatedDuration == null) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            long linesCount = resolveChangeLogLines(descriptor.getChangeLog());
            estimatedDuration = 100 + (linesCount * 2);
            logger.trace("Resolved {} changelog lines in {}", linesCount, stopwatch);
        }
        return estimatedDuration;
    }

    @Override
    public void prepare(DataSource dataSource) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        SpringLiquibase liquibase = new SpringLiquibase();
        descriptor.applyTo(liquibase);
        liquibase.setDataSource(dataSource);

        try {
            liquibase.afterPropertiesSet();
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Unexpected error when running Liquibase", e);
        }

        logger.trace("Database has been successfully prepared in {}", stopwatch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiquibaseDatabasePreparer that = (LiquibaseDatabasePreparer) o;
        return Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("changeLog", descriptor.getChangeLog())
                .add("estimatedDuration", estimatedDuration())
                .toString();
    }

    protected long resolveChangeLogLines(String path) {
        try {
            String changeLogPath = path.replace('\\', '/');
            InputStream changeLogStream = openChangeLogStream(changeLogPath);
            if (changeLogStream == null) {
                throw new ChangeLogParseException(changeLogPath + " does not exist");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(changeLogStream))) {
                return reader.lines().count();
            }
        } catch (Exception e) {
            logger.warn("Unexpected error when resolving liquibase changelog", e);
            return 10000; // fallback value
        }
    }

    protected InputStream openChangeLogStream(String changeLogPath) throws IOException, ClassNotFoundException {
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setResourceLoader(descriptor.getResourceLoader());
        if (ClassUtils.isPresent("liquibase.integration.spring.SpringLiquibase$SpringResourceOpener", null)) {
            return StreamUtil.openStream(changeLogPath, null, null, null);
        } else if (ClassUtils.isPresent("liquibase.integration.spring.SpringLiquibase$SpringResourceAccessor", null)) {
            Object resourceAccessor = ReflectionUtils.invokeConstructor("liquibase.integration.spring.SpringLiquibase$SpringResourceAccessor", springLiquibase);
            return ReflectionUtils.invokeMethod(resourceAccessor, "openStream", null, changeLogPath);
        } else {
            Object resourceAccessor = ReflectionUtils.invokeConstructor("liquibase.integration.spring.SpringResourceAccessor", descriptor.getResourceLoader());
            return ReflectionUtils.invokeMethod(resourceAccessor, "openStream", null, changeLogPath);
        }
    }
}
