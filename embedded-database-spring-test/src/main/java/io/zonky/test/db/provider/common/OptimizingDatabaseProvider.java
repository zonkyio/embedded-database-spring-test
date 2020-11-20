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

package io.zonky.test.db.provider.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class OptimizingDatabaseProvider implements DatabaseProvider {

    public static final CompositeDatabasePreparer EMPTY_PREPARER = new CompositeDatabasePreparer(Collections.emptyList());

    private static final Logger logger = LoggerFactory.getLogger(OptimizingDatabaseProvider.class);

    private static final Set<BaselineKey> baselines = Sets.newConcurrentHashSet();
    private static final AtomicLongMap<BaselineKey> requestCount = AtomicLongMap.create();

    private final DatabaseProvider provider;

    public OptimizingDatabaseProvider(DatabaseProvider provider) {
        this.provider = provider;
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
        CompositeDatabasePreparer compositePreparer = preparer instanceof CompositeDatabasePreparer ?
                (CompositeDatabasePreparer) preparer : new CompositeDatabasePreparer(ImmutableList.of(preparer));
        List<DatabasePreparer> preparers = compositePreparer.getPreparers();

        for (int i = preparers.size(); i > 0; i--) {
            requestCount.incrementAndGet(new BaselineKey(provider, new CompositeDatabasePreparer(preparers.subList(0, i))));
        }

        for (int i = preparers.size(); i > 0; i--) {
            CompositeDatabasePreparer baselinePreparer = new CompositeDatabasePreparer(preparers.subList(0, i));
            BaselineKey baselineKey = new BaselineKey(provider, baselinePreparer);

            if (requestCount.get(baselineKey) >= 3 && !baselines.contains(baselineKey)) {
                logger.trace("Creating a new baseline preparer {} because the preparer has reached the maximum request threshold", baselinePreparer);
                baselines.add(baselineKey);
            }

            if (baselines.contains(baselineKey)) {
                CompositeDatabasePreparer complementaryPreparer = new CompositeDatabasePreparer(preparers.subList(i, preparers.size()));

                if (i == preparers.size()) {
                    logger.trace("Baseline preparer found, creating database by using the existing baseline preparer {}", baselinePreparer);
                    return createDatabase(baselinePreparer, EMPTY_PREPARER);
                } else if (hasSlowOperation(complementaryPreparer)) {
                    logger.trace("Baseline preparer found {}, using the existing preparer to create a new baseline preparer {}", baselinePreparer, compositePreparer);
                    baselines.add(new BaselineKey(provider, compositePreparer));
                    return createDatabase(compositePreparer, EMPTY_PREPARER);
                } else {
                    logger.trace("Baseline preparer found {}, creating database by using a complementary preparer {}", baselinePreparer, complementaryPreparer);
                    return createDatabase(baselinePreparer, complementaryPreparer);
                }
            }
        }

        logger.trace("No baseline preparer found, creating database by using a new baseline preparer {}", compositePreparer);
        baselines.add(new BaselineKey(provider, compositePreparer));
        return createDatabase(compositePreparer, EMPTY_PREPARER);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptimizingDatabaseProvider that = (OptimizingDatabaseProvider) o;
        return Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider);
    }

    private EmbeddedDatabase createDatabase(CompositeDatabasePreparer baselinePreparer, CompositeDatabasePreparer complementaryPreparer) {
        EmbeddedDatabase database = provider.createDatabase(baselinePreparer);
        try {
            complementaryPreparer.prepare(database);
        } catch (SQLException e) {
            throw new IllegalStateException("Unknown error when applying the preparer", e);
        }
        return database;
    }

    private boolean hasSlowOperation(CompositeDatabasePreparer preparer) {
        return preparer.estimatedDuration() > 150;
    }

    private static class BaselineKey {

        private final DatabaseProvider provider;
        private final DatabasePreparer preparer;

        private BaselineKey(DatabaseProvider provider, DatabasePreparer preparer) {
            this.provider = provider;
            this.preparer = preparer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BaselineKey that = (BaselineKey) o;
            return Objects.equals(provider, that.provider) &&
                    Objects.equals(preparer, that.preparer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, preparer);
        }
    }
}
