/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.opentable.db.postgres.embedded.DatabasePreparer;
import com.opentable.db.postgres.embedded.PreparedDbProvider;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkState;

/**
 * Default implementation of {@link FlywayDataSourceContext} that is used for deferred initialization of the embedded database.
 * Note that this target source is dynamic and supports hot reloading while the application is running.
 * <p/>
 * For the reloading of the underlying data source is used cacheable {@link com.opentable.db.postgres.embedded.DatabasePreparer},
 * which can utilize a special template database to effective copy data into multiple independent databases.
 *
 * @see io.zonky.test.db.postgres.FlywayEmbeddedPostgresDataSourceFactoryBean
 * @see OptimizedFlywayTestExecutionListener
 * @see <a href="https://www.postgresql.org/docs/9.6/static/manage-ag-templatedbs.html">Template Databases</a>
 */
public class DefaultFlywayDataSourceContext implements FlywayDataSourceContext {

    protected static final LoadingCache<Integer, Semaphore> CONNECTION_SEMAPHORES = CacheBuilder.newBuilder()
            .build(new CacheLoader<Integer, Semaphore>() {
                public Semaphore load(Integer key) {
                    return new Semaphore(100); // the maximum number of simultaneous connections to the database
                }
            });

    protected static final ThreadLocal<DataSource> preparerDataSourceHolder = new ThreadLocal<>();

    protected volatile DataSource dataSource;

    @Override
    public Class<?> getTargetClass() {
        return DataSource.class;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public Object getTarget() throws Exception {
        DataSource dataSource = preparerDataSourceHolder.get();

        if (dataSource == null) {
            dataSource = this.dataSource;
        }

        checkState(dataSource != null, "dataSource is not initialized yet");
        return dataSource;
    }

    @Override
    public void releaseTarget(Object target) throws Exception {
        // nothing to do
    }

    @Override
    public void reload(Flyway flyway) throws Exception {
        FlywayDatabasePreparer preparer = new FlywayDatabasePreparer(flyway);
        PreparedDbProvider provider = PreparedDbProvider.forPreparer(preparer);

        PGSimpleDataSource dataSource = ((PGSimpleDataSource) provider.createDataSource());
        Semaphore semaphore = CONNECTION_SEMAPHORES.get(dataSource.getPortNumber());
        this.dataSource = new BlockingDataSourceWrapper(dataSource, semaphore);
    }

    protected FlywayConfigSnapshot createConfigSnapshot(Flyway flyway) {
        FlywayConfigSnapshot configSnapshot = new FlywayConfigSnapshot(flyway);
        checkState(ArrayUtils.isNotEmpty(configSnapshot.getSchemas()),
                "org.flywaydb.core.Flyway#schemaNames must be specified");
        return configSnapshot;
    }

    protected class FlywayDatabasePreparer implements DatabasePreparer {

        private final FlywayConfigSnapshot configSnapshot;
        private final Flyway flyway;

        public FlywayDatabasePreparer(Flyway flyway) {
            this.configSnapshot = createConfigSnapshot(flyway);
            this.flyway = flyway;
        }

        @Override
        public void prepare(DataSource ds) throws SQLException {
            preparerDataSourceHolder.set(ds);
            try {
                flyway.migrate();
            } finally {
                preparerDataSourceHolder.remove();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlywayDatabasePreparer that = (FlywayDatabasePreparer) o;
            return Objects.equals(configSnapshot, that.configSnapshot);
        }

        @Override
        public int hashCode() {
            return Objects.hash(configSnapshot);
        }
    }
}
