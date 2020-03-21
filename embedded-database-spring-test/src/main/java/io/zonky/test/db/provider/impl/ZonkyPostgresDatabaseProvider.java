/*
 * Copyright 2019 the original author or authors.
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

package io.zonky.test.db.provider.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.zonky.test.db.flyway.BlockingDataSourceWrapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseRequest;
import io.zonky.test.db.provider.DatabaseTemplate;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.PostgresEmbeddedDatabase;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import io.zonky.test.db.util.PropertyUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;

public class ZonkyPostgresDatabaseProvider implements TemplatableDatabaseProvider {

    private static final int MAX_DATABASE_CONNECTIONS = 300;

    private static final LoadingCache<ClusterKey, DatabaseInstance> databases = CacheBuilder.newBuilder()
            .build(new CacheLoader<ClusterKey, DatabaseInstance>() {
                public DatabaseInstance load(ClusterKey key) throws IOException {
                    return new DatabaseInstance(key.databaseConfig);
                }
            });

    private final DatabaseConfig databaseConfig;
    private final ClientConfig clientConfig;

    public ZonkyPostgresDatabaseProvider(Environment environment, AutowireCapableBeanFactory beanFactory) {
        String preparerIsolation = environment.getProperty("zonky.test.database.postgres.zonky-provider.preparer-isolation", "database");
        PreparerIsolation isolation = PreparerIsolation.valueOf(preparerIsolation.toUpperCase());

        Map<String, String> initdbProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.initdb.properties");
        Map<String, String> configProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.server.properties");
        Map<String, String> connectProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.client.properties");

        ConditionalParameters parameters = (ConditionalParameters) beanFactory.autowire(
                ConditionalParameters.class, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false);

        this.databaseConfig = new DatabaseConfig(initdbProperties, configProperties, parameters.customizers, isolation);
        this.clientConfig = new ClientConfig(connectProperties);
    }

    @Override
    public DatabaseTemplate createTemplate(DatabaseRequest request) throws Exception {
        EmbeddedDatabase result = createDatabase(request);
        return new DatabaseTemplate(result.getDatabaseName());
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabaseRequest request) throws Exception {
        ClusterKey clusterKey = new ClusterKey(databaseConfig, clientConfig, request.getPreparer()); // TODO: request.getPreparer()
        DatabaseInstance instance = databases.getUnchecked(clusterKey);
        return instance.createDatabase(clientConfig, request);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZonkyPostgresDatabaseProvider that = (ZonkyPostgresDatabaseProvider) o;
        return Objects.equals(databaseConfig, that.databaseConfig) &&
                Objects.equals(clientConfig, that.clientConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseConfig, clientConfig);
    }

    protected static class DatabaseInstance {

        private final EmbeddedPostgres postgres;
        private final Semaphore semaphore;

        private DatabaseInstance(DatabaseConfig config) throws IOException {
            EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
            config.applyTo(builder);

            postgres = builder.start();
            semaphore = new Semaphore(MAX_DATABASE_CONNECTIONS);
        }

        public EmbeddedDatabase createDatabase(ClientConfig config, DatabaseRequest request) throws SQLException {
            DatabaseTemplate template = request.getTemplate();
            DatabasePreparer preparer = request.getPreparer();

            String databaseName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);

            if (template != null) {
                executeStatement(config, String.format("CREATE DATABASE %s TEMPLATE %s OWNER %s ENCODING 'utf8'", databaseName, template.getTemplateName(), "postgres"));
            } else {
                executeStatement(config, String.format("CREATE DATABASE %s OWNER %s ENCODING 'utf8'", databaseName, "postgres"));
            }

            EmbeddedDatabase database = getDatabase(config, databaseName);

            if (preparer != null) {
                preparer.prepare(database);
            }

            return database;
        }

        private void dropDatabase(ClientConfig config, String dbName) throws SQLException {
            executeStatement(config, String.format("DROP DATABASE IF EXISTS %s", dbName));
        }

        private void executeStatement(ClientConfig config, String ddlStatement) throws SQLException {
            DataSource dataSource = getDatabase(config, "postgres");
            try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(ddlStatement)) {
                stmt.execute();
            }
        }

        private EmbeddedDatabase getDatabase(ClientConfig config, String dbName) {
            PGSimpleDataSource dataSource = (PGSimpleDataSource) postgres.getDatabase("postgres", dbName, config.connectProperties);
            return new BlockingDataSourceWrapper(new PostgresEmbeddedDatabase(dataSource, () -> dropDatabase(config, dbName)), semaphore);
        }
    }

    private static class DatabaseConfig {

        private final Map<String, String> initdbProperties;
        private final Map<String, String> configProperties;
        private final List<Consumer<EmbeddedPostgres.Builder>> customizers;
        private final EmbeddedPostgres.Builder builder;
        private final PreparerIsolation isolation;

        private DatabaseConfig(Map<String, String> initdbProperties, Map<String, String> configProperties, List<Consumer<EmbeddedPostgres.Builder>> customizers, PreparerIsolation isolation) {
            this.initdbProperties = ImmutableMap.copyOf(initdbProperties);
            this.configProperties = ImmutableMap.copyOf(configProperties);
            this.customizers = ImmutableList.copyOf(customizers);
            this.isolation = isolation;
            this.builder = EmbeddedPostgres.builder();
            applyTo(this.builder);
        }

        public final void applyTo(EmbeddedPostgres.Builder builder) {
            builder.setPGStartupWait(Duration.ofSeconds(20L));
            initdbProperties.forEach(builder::setLocaleConfig);
            configProperties.forEach(builder::setServerConfig);
            customizers.forEach(c -> c.accept(builder));
            builder.setServerConfig("max_connections", String.valueOf(MAX_DATABASE_CONNECTIONS));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DatabaseConfig that = (DatabaseConfig) o;
            return Objects.equals(builder, that.builder) &&
                    Objects.equals(isolation, that.isolation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(builder, isolation);
        }
    }

    private static class ClientConfig {

        private final Map<String, String> connectProperties;

        private ClientConfig(Map<String, String> connectProperties) {
            this.connectProperties = ImmutableMap.copyOf(connectProperties);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClientConfig that = (ClientConfig) o;
            return Objects.equals(connectProperties, that.connectProperties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectProperties);
        }
    }

    private static class ClusterKey {

        private final DatabaseConfig databaseConfig;
        private final ClientConfig clientConfig;
        private final DatabasePreparer preparer;

        private ClusterKey(DatabaseConfig databaseConfig, ClientConfig clientConfig, DatabasePreparer preparer) {
            this.databaseConfig = databaseConfig;

            if (databaseConfig.isolation == PreparerIsolation.CLUSTER) {
                this.clientConfig = clientConfig;
                this.preparer = preparer;
            } else {
                this.clientConfig = null;
                this.preparer = null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClusterKey that = (ClusterKey) o;
            return Objects.equals(databaseConfig, that.databaseConfig) &&
                    Objects.equals(clientConfig, that.clientConfig) &&
                    Objects.equals(preparer, that.preparer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(databaseConfig, clientConfig, preparer);
        }
    }

    private enum PreparerIsolation {

        /**
         * All databases are stored within a single database cluster.
         */
        DATABASE,

        /**
         * A new database cluster is created for each template database.
         */
        CLUSTER

    }

    protected static class ConditionalParameters {

        private final List<Consumer<EmbeddedPostgres.Builder>> customizers;

        public ConditionalParameters(@Autowired(required = false) List<Consumer<EmbeddedPostgres.Builder>> customizers) {
            this.customizers = Optional.ofNullable(customizers).orElse(emptyList());;
        }
    }
}
