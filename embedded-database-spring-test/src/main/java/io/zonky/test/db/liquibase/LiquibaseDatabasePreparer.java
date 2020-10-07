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
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.integration.spring.SpringLiquibase.SpringResourceOpener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Objects;

public class LiquibaseDatabasePreparer implements DatabasePreparer {

    private static final Logger logger = LoggerFactory.getLogger(LiquibaseDatabasePreparer.class);

    private final LiquibaseDescriptor descriptor;
    private final long estimatedDuration;

    public LiquibaseDatabasePreparer(LiquibaseDescriptor descriptor) {
        this.descriptor = descriptor;

        // TODO: finish it
        String changeLogPath = descriptor.getChangeLog().replace('\\', '/'); //convert to standard / if using absolute path on windows
        SpringResourceOpener resourceAccessor = new SpringLiquibase().new SpringResourceOpener(descriptor.getChangeLog());
        Resource resource = resourceAccessor.getResource(changeLogPath);
        long changeLogSize; // TODO: use size in lines instead of bytes
        try {
            changeLogSize = resource.contentLength();
        } catch (IOException e) {
            changeLogSize = 100_000;
        }
        this.estimatedDuration = changeLogSize;
    }

    @Override
    public long estimatedDuration() {
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
                .toString();
    }
}
