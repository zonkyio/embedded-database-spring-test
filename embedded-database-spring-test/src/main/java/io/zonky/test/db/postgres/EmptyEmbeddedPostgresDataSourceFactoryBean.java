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

package io.zonky.test.db.postgres;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import io.zonky.test.db.flyway.BlockingDataSourceWrapper;
import io.zonky.test.db.logging.EmbeddedDatabaseReporter;
import io.zonky.test.db.postgres.embedded.DatabasePreparer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres.Builder;
import io.zonky.test.db.postgres.embedded.PreparedDbProvider;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Implementation of the {@link org.springframework.beans.factory.FactoryBean} interface
 * that provides empty instances of the embedded postgres database.
 */
public class EmptyEmbeddedPostgresDataSourceFactoryBean implements FactoryBean<DataSource>, InitializingBean {

    protected static final int MAX_DATABASE_CONNECTIONS = 300;

    protected static final Consumer<Builder> DEFAULT_DATABASE_CONFIGURATION = builder -> {
        builder.setPGStartupWait(Duration.ofSeconds(20L));
    };

    protected static final Consumer<Builder> FORCED_DATABASE_CONFIGURATION =
            builder -> builder.setServerConfig("max_connections", String.valueOf(MAX_DATABASE_CONNECTIONS));

    protected static final LoadingCache<Integer, Semaphore> CONNECTION_SEMAPHORES = CacheBuilder.newBuilder()
            .build(new CacheLoader<Integer, Semaphore>() {
                public Semaphore load(Integer key) {
                    return new Semaphore(MAX_DATABASE_CONNECTIONS);
                }
            });

    @Autowired(required = false)
    protected List<Consumer<Builder>> databaseCustomizers = new ArrayList<>();

    private DataSource dataSource;

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Class<?> getObjectType() {
        return DataSource.class;
    }

    @Override
    public DataSource getObject() throws Exception {
        return dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        List<Consumer<Builder>> customizers = ImmutableList.<Consumer<Builder>>builder()
                .add(DEFAULT_DATABASE_CONFIGURATION)
                .addAll(databaseCustomizers)
                .add(FORCED_DATABASE_CONFIGURATION)
                .build();

        PreparedDbProvider provider = PreparedDbProvider.forPreparer(EmptyDatabasePreparer.INSTANCE, customizers);
        PGSimpleDataSource dataSource = provider.createDataSource().unwrap(PGSimpleDataSource.class);
        EmbeddedDatabaseReporter.reportDataSource(dataSource);
        Semaphore semaphore = CONNECTION_SEMAPHORES.get(dataSource.getPortNumber());
        this.dataSource = new BlockingDataSourceWrapper(dataSource, semaphore);
    }

    private static class EmptyDatabasePreparer implements DatabasePreparer {

        public static final EmptyDatabasePreparer INSTANCE = new EmptyDatabasePreparer();

        private EmptyDatabasePreparer() {
            // direct initialization is forbidden
        }

        @Override
        public void prepare(DataSource dataSource) throws SQLException {
            // nothing to do
        }
    }
}
