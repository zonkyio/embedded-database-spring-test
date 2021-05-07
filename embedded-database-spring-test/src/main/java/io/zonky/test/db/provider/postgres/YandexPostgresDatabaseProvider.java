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

package io.zonky.test.db.provider.postgres;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import de.flapdoodle.embed.process.distribution.GenericVersion;
import de.flapdoodle.embed.process.distribution.IVersion;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.support.BlockingDatabaseWrapper;
import io.zonky.test.db.provider.DatabaseRequest;
import io.zonky.test.db.provider.DatabaseTemplate;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import io.zonky.test.db.provider.support.SimpleDatabaseTemplate;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import io.zonky.test.db.util.PropertyUtils;
import io.zonky.test.db.util.RandomStringUtils;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.ds.common.BaseDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.util.SocketUtil;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.yandex.qatools.embed.postgresql.EmbeddedPostgres.DEFAULT_DB_NAME;
import static ru.yandex.qatools.embed.postgresql.EmbeddedPostgres.DEFAULT_HOST;
import static ru.yandex.qatools.embed.postgresql.EmbeddedPostgres.defaultRuntimeConfig;

public class YandexPostgresDatabaseProvider implements TemplatableDatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(YandexPostgresDatabaseProvider.class);

    private static final String POSTGRES_USERNAME = "postgres";
    private static final String POSTGRES_PASSWORD = "yandex";

    private static final LoadingCache<DatabaseConfig, DatabaseInstance> databases = CacheBuilder.newBuilder()
            .build(new CacheLoader<DatabaseConfig, DatabaseInstance>() {
                public DatabaseInstance load(DatabaseConfig config) throws IOException {
                    return new DatabaseInstance(config);
                }
            });

    private final DatabaseConfig databaseConfig;
    private final ClientConfig clientConfig;

    public YandexPostgresDatabaseProvider(Environment environment) {
        String postgresVersion = environment.getProperty("zonky.test.database.postgres.yandex-provider.postgres-version", "11.10-1");

        Map<String, String> initdbProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.initdb.properties");
        Map<String, String> configProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.server.properties");
        Map<String, String> connectProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.client.properties");

        this.databaseConfig = new DatabaseConfig(new GenericVersion(postgresVersion), initdbProperties, configProperties);
        this.clientConfig = new ClientConfig(connectProperties);
    }

    @Override
    public DatabaseTemplate createTemplate(DatabaseRequest request) throws ProviderException {
        try {
            EmbeddedDatabase result = createDatabase(request);
            BaseDataSource dataSource = result.unwrap(BaseDataSource.class);
            return new SimpleDatabaseTemplate(dataSource.getDatabaseName(), result::close);
        } catch (SQLException e) {
            throw new ProviderException("Unexpected error when creating a database template", e);
        }
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabaseRequest request) throws ProviderException {
        try {
            DatabaseInstance instance = databases.get(databaseConfig);
            return instance.createDatabase(clientConfig, request);
        } catch (ExecutionException | UncheckedExecutionException e) {
            Throwables.throwIfInstanceOf(e.getCause(), ProviderException.class);
            throw new ProviderException("Unexpected error when preparing a database cluster", e.getCause());
        } catch (SQLException e) {
            throw new ProviderException("Unexpected error when creating a database", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YandexPostgresDatabaseProvider that = (YandexPostgresDatabaseProvider) o;
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
            Map<String, String> initdbProperties = new HashMap<>(config.initdbProperties);
            initdbProperties.putIfAbsent("encoding", "UTF-8");

            List<String> initdbParams = initdbProperties.entrySet().stream()
                    .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

            Map<String, String> serverProperties = new HashMap<>(config.configProperties);
            serverProperties.putIfAbsent("max_connections", "300");

            List<String> postgresParams = serverProperties.entrySet().stream()
                    .flatMap(e -> Stream.of("-c", String.format("%s=%s", e.getKey(), e.getValue())))
                    .collect(Collectors.toList());

            postgres = new EmbeddedPostgres(config.version);
            postgres.start(defaultRuntimeConfig(), DEFAULT_HOST, SocketUtil.findFreePort(),
                    DEFAULT_DB_NAME, POSTGRES_USERNAME, POSTGRES_PASSWORD, initdbParams, postgresParams);

            Runtime.getRuntime().addShutdownHook(new Thread(postgres::close));

            semaphore = new Semaphore(Integer.parseInt(serverProperties.get("max_connections")));
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

            try {
                EmbeddedDatabase database = getDatabase(config, databaseName);
                if (preparer != null) {
                    preparer.prepare(database);
                }
                return database;
            } catch (Exception e) {
                dropDatabase(config, databaseName);
                throw e;
            }
        }

        private void dropDatabase(ClientConfig config, String dbName) {
            CompletableFuture.runAsync(() -> {
                try {
                    executeStatement(config, String.format("DROP DATABASE IF EXISTS %s", dbName));
                } catch (Exception e) {
                    if (logger.isTraceEnabled()) {
                        logger.warn("Unable to release '{}' database", dbName, e);
                    } else {
                        logger.warn("Unable to release '{}' database", dbName);
                    }
                }
            });
        }

        private void executeStatement(ClientConfig config, String ddlStatement) throws SQLException {
            DataSource dataSource = getDatabase(config, "postgres");
            try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(ddlStatement)) {
                stmt.execute();
            }
        }

        private EmbeddedDatabase getDatabase(ClientConfig config, String dbName) throws SQLException {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();

            dataSource.setServerName(DEFAULT_HOST);
            dataSource.setPortNumber(postgres.getConfig().map(cfg -> cfg.net().port()).orElse(-1));
            dataSource.setDatabaseName(dbName);

            dataSource.setUser(POSTGRES_USERNAME);
            dataSource.setPassword(POSTGRES_PASSWORD);

            for (Map.Entry<String, String> entry : config.connectProperties.entrySet()) {
                dataSource.setProperty(entry.getKey(), entry.getValue());
            }

            return new BlockingDatabaseWrapper(new PostgresEmbeddedDatabase(dataSource, () -> dropDatabase(config, dbName)), semaphore);
        }
    }

    private static class DatabaseConfig {

        private final IVersion version;
        private final Map<String, String> initdbProperties;
        private final Map<String, String> configProperties;

        private DatabaseConfig(IVersion version, Map<String, String> initdbProperties, Map<String, String> configProperties) {
            this.version = version;
            this.initdbProperties = ImmutableMap.copyOf(initdbProperties);
            this.configProperties = ImmutableMap.copyOf(configProperties);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DatabaseConfig that = (DatabaseConfig) o;
            return Objects.equals(version, that.version) &&
                    Objects.equals(initdbProperties, that.initdbProperties) &&
                    Objects.equals(configProperties, that.configProperties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, initdbProperties, configProperties);
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
}
