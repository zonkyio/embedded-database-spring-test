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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.concurrent.CompletableToListenableFutureAdapter;
import org.springframework.util.concurrent.ListenableFuture;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
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

    protected static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;

    protected static final LoadingCache<Integer, Semaphore> CONNECTION_SEMAPHORES = CacheBuilder.newBuilder()
            .build(new CacheLoader<Integer, Semaphore>() {
                public Semaphore load(Integer key) {
                    return new Semaphore(100); // the maximum number of simultaneous connections to the database
                }
            });

    protected static final ThreadLocal<DataSource> preparerDataSourceHolder = new ThreadLocal<>();

    protected volatile CompletableFuture<DataSource> dataSourceFuture = CompletableFuture.completedFuture(null);

    protected int maxAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;

    protected TaskExecutor bootstrapExecutor;

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
        DataSource threadBoundDataSource = preparerDataSourceHolder.get();
        if (threadBoundDataSource != null) {
            return threadBoundDataSource;
        }

        if (bootstrapExecutor == null && !dataSourceFuture.isDone()) {
            throw new IllegalStateException("dataSource is not initialized yet");
        }

        DataSource dataSource = dataSourceFuture.get();
        checkState(dataSource != null, "Unexpected error occurred while initializing the data source");
        return dataSource;
    }

    @Override
    public void releaseTarget(Object target) throws Exception {
        // nothing to do
    }

    @Override
    public synchronized ListenableFuture<Void> reload(Flyway flyway) {
        Executor executor = bootstrapExecutor != null ? bootstrapExecutor : Runnable::run;

        CompletableFuture<DataSource> reloadFuture = dataSourceFuture.thenApplyAsync(x -> {
            for (int current = 1; current <= maxAttempts; current++) {
                try {
                    FlywayDatabasePreparer preparer = new FlywayDatabasePreparer(flyway);
                    PreparedDbProvider provider = PreparedDbProvider.forPreparer(preparer);

                    PGSimpleDataSource dataSource = ((PGSimpleDataSource) provider.createDataSource());
                    Semaphore semaphore = CONNECTION_SEMAPHORES.get(dataSource.getPortNumber());
                    return new BlockingDataSourceWrapper(dataSource, semaphore);
                } catch (Exception e) {
                    if (ExceptionUtils.indexOfType(e, IOException.class) == -1 || current == maxAttempts) {
                        throw new CompletionException(e);
                    }
                }
            }
            throw new IllegalStateException("maxAttempts parameter must be greater or equal to 1");
        }, executor);

        // main data source future must never fail, otherwise all following tests will fail
        dataSourceFuture = reloadFuture.exceptionally(throwable -> null);

        return new CompletableToListenableFutureAdapter<>(reloadFuture.thenApply(dataSource -> null));
    }

    /**
     * Set the number of attempts for initialization of the embedded database.
     * Includes the initial attempt before the retries begin so, generally, will be {@code >= 1}.
     * For example setting this property to 3 means 3 attempts total (initial + 2 retries).
     *
     * @param maxAttempts the maximum number of attempts including the initial attempt.
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * Specify an asynchronous executor for background bootstrapping,
     * e.g. a {@link org.springframework.core.task.SimpleAsyncTaskExecutor}.
     * <p>
     * {@code DataSource} initialization will then switch into background
     * bootstrap mode, with a {@code DataSource} proxy immediately returned for
     * injection purposes instead of waiting for the Flyway's bootstrapping to complete.
     * However, note that the first actual call to a {@code DataSource} method will
     * then block until the Flyway's bootstrapping completed, if not ready by then.
     * For maximum benefit, make sure to avoid early {@code DataSource} calls
     * in init methods of related beans.
     * <p>
     * Inspired by {@code org.springframework.orm.jpa.AbstractEntityManagerFactoryBean#setBootstrapExecutor}.
     */
    public void setBootstrapExecutor(TaskExecutor bootstrapExecutor) {
        this.bootstrapExecutor = bootstrapExecutor;
    }

    protected FlywayConfigSnapshot createConfigSnapshot(Flyway flyway) {
        return new FlywayConfigSnapshot(flyway);
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
