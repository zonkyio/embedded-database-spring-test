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

package io.zonky.test.db.flyway.preparer;

import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;
import io.zonky.test.db.flyway.FlywayDescriptor;
import io.zonky.test.db.flyway.FlywayWrapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.shaded.org.apache.commons.lang.StringUtils;

import java.util.Objects;
import java.util.stream.Stream;

public class MigrateFlywayDatabasePreparer extends FlywayDatabasePreparer {

    private volatile Long estimatedDuration;

    public MigrateFlywayDatabasePreparer(FlywayDescriptor descriptor) {
        super(descriptor);
    }

    public MigrateFlywayDatabasePreparer(FlywayDescriptor descriptor, long estimatedDuration) {
        super(descriptor);
        this.estimatedDuration = estimatedDuration;
    }

    @Override
    public long estimatedDuration() {
        if (estimatedDuration == null) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            long migrationsCount = descriptor.getLocations().stream()
                    .map(location -> location.replaceFirst("^filesystem:", "file:"))
                    .mapToLong(location -> resolveMigrations(resolver, location))
                    .sum();

            estimatedDuration = 25 + (25 * migrationsCount);
            logger.trace("Resolved {} migrations in {}", migrationsCount, stopwatch);
        }
        return estimatedDuration;
    }

    @Override
    protected Object doOperation(FlywayWrapper wrapper) {
        return wrapper.migrate();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("schemas", descriptor.getSchemas())
                .add("locations", descriptor.getLocations())
                .add("estimatedDuration", estimatedDuration())
                .toString();
    }

    protected long resolveMigrations(PathMatchingResourcePatternResolver resolver, String location) {
        String[] migrationPrefixes = new String[] { descriptor.getSqlMigrationPrefix(), descriptor.getRepeatableSqlMigrationPrefix() };
        String[] migrationSuffixes = descriptor.getSqlMigrationSuffixes().toArray(new String[0]);

        try {
            return Stream.of(resolver.getResources(location + "/**/*"))
                    .map(Resource::getFilename)
                    .filter(Objects::nonNull)
                    .filter(filename -> {
                        boolean isSqlMigration = StringUtils.startsWithAny(filename, migrationPrefixes)
                                && StringUtils.endsWithAny(filename, migrationSuffixes);
                        return isSqlMigration || StringUtils.endsWith(filename, ".class");
                    })
                    .count();
        } catch (Exception e) {
            logger.warn("Unexpected error when resolving flyway locations", e);
            return 200; // fallback value
        }
    }
}
