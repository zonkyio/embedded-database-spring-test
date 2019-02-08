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
import com.google.common.collect.ImmutableMap;
import de.flapdoodle.embed.process.distribution.GenericVersion;
import de.flapdoodle.embed.process.distribution.IVersion;
import io.zonky.test.db.flyway.BlockingDataSourceWrapper;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.DatabaseType;
import io.zonky.test.db.provider.ProviderType;
import io.zonky.test.db.util.PropertyUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.postgresql.ds.PGSimpleDataSource;
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
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.yandex.qatools.embed.postgresql.EmbeddedPostgres.DEFAULT_DB_NAME;
import static ru.yandex.qatools.embed.postgresql.EmbeddedPostgres.DEFAULT_HOST;
import static ru.yandex.qatools.embed.postgresql.EmbeddedPostgres.defaultRuntimeConfig;

public class YandexPostgresDatabaseProvider implements DatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(YandexPostgresDatabaseProvider.class);

    private static final String POSTGRES_USERNAME = "postgres";
    private static final String POSTGRES_PASSWORD = "yandex";

    private static final LoadingCache<DatabaseConfig, DatabaseInstance> databases = CacheBuilder.newBuilder()
            .build(new CacheLoader<DatabaseConfig, DatabaseInstance>() {
                public DatabaseInstance load(DatabaseConfig config) throws IOException {
                    try {
                        return new DatabaseInstance(config);
                    } catch (NoClassDefFoundError e) {
                        logger.error("\n\nHINT: You may have to add ru.yandex.qatools.embed:postgresql-embedded dependency!!!\n");
                        throw e;
                    }
                }
            });

    private final DatabaseConfig databaseConfig;
    private final ClientConfig clientConfig;

    public YandexPostgresDatabaseProvider(Environment environment) {
        String postgresVersion = environment.getProperty("embedded-database.postgres.yandex.version", "10.6-1");

        Map<String, String> initdbProperties = PropertyUtils.extractAll(environment, "embedded-database.postgres.initdb.properties");
        Map<String, String> configProperties = PropertyUtils.extractAll(environment, "embedded-database.postgres.server.properties");
        Map<String, String> connectProperties = PropertyUtils.extractAll(environment, "embedded-database.postgres.client.properties");

        this.databaseConfig = new DatabaseConfig(new GenericVersion(postgresVersion), initdbProperties, configProperties);
        this.clientConfig = new ClientConfig(connectProperties);
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRES;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.YANDEX;
    }

    @Override
    public DataSource getDatabase(DatabasePreparer preparer) throws SQLException {
        DatabaseInstance instance = databases.getUnchecked(databaseConfig);
        DatabaseInstance.DatabaseTemplate template = instance.getTemplate(clientConfig, preparer);
        return template.createDatabase();
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

        private final LoadingCache<TemplateKey, DatabaseTemplate> templates = CacheBuilder.newBuilder()
                .build(new CacheLoader<TemplateKey, DatabaseTemplate>() {
                    public DatabaseTemplate load(TemplateKey key) throws Exception {
                        return new DatabaseTemplate(key.config, key.preparer);
                    }
                });

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

        public DatabaseTemplate getTemplate(ClientConfig config, DatabasePreparer preparer) {
            return templates.getUnchecked(new TemplateKey(config, preparer));
        }

        protected class DatabaseTemplate {

            private final ClientConfig config;
            private final String templateName;

            private DatabaseTemplate(ClientConfig config, DatabasePreparer preparer) throws SQLException {
                this.config = config;
                this.templateName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);

                executeStatement(String.format("CREATE DATABASE %s OWNER %s ENCODING 'utf8'", templateName, "postgres"));
                DataSource dataSource = getDatabase(templateName);
                preparer.prepare(dataSource);
            }

            public DataSource createDatabase() throws SQLException {
                String databaseName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);
                executeStatement(String.format("CREATE DATABASE %s TEMPLATE %s OWNER %s ENCODING 'utf8'", databaseName, templateName, "postgres"));
                return getDatabase(databaseName);
            }

            private void executeStatement(String ddlStatement) throws SQLException {
                DataSource dataSource = getDatabase("postgres");
                try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(ddlStatement)) {
                    stmt.execute();
                }
            }

            private DataSource getDatabase(String dbName) throws SQLException {
                PGSimpleDataSource dataSource = new PGSimpleDataSource();

                dataSource.setServerName(DEFAULT_HOST);
                dataSource.setPortNumber(postgres.getConfig().map(config -> config.net().port()).orElse(-1));
                dataSource.setDatabaseName(dbName);

                dataSource.setUser(POSTGRES_USERNAME);
                dataSource.setPassword(POSTGRES_PASSWORD);

                for (Map.Entry<String, String> entry : config.connectProperties.entrySet()) {
                    dataSource.setProperty(entry.getKey(), entry.getValue());
                }

                return new BlockingDataSourceWrapper(dataSource, semaphore);
            }
        }

        protected static class TemplateKey {

            private final ClientConfig config;
            private final DatabasePreparer preparer;

            private TemplateKey(ClientConfig config, DatabasePreparer preparer) {
                this.config = config;
                this.preparer = preparer;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                TemplateKey that = (TemplateKey) o;
                return Objects.equals(config, that.config) &&
                        Objects.equals(preparer, that.preparer);
            }

            @Override
            public int hashCode() {
                return Objects.hash(config, preparer);
            }
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
