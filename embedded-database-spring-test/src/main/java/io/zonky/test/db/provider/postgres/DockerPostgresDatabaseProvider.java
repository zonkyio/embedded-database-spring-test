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

import com.cedarsoftware.util.DeepEquals;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseRequest;
import io.zonky.test.db.provider.DatabaseTemplate;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import io.zonky.test.db.provider.support.BlockingDatabaseWrapper;
import io.zonky.test.db.provider.support.SimpleDatabaseTemplate;
import io.zonky.test.db.util.PropertyUtils;
import io.zonky.test.db.util.RandomStringUtils;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.ds.common.BaseDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;

public class DockerPostgresDatabaseProvider implements TemplatableDatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(DockerPostgresDatabaseProvider.class);

    private static final String DEFAULT_POSTGRES_USERNAME = "postgres";
    private static final String DEFAULT_POSTGRES_PASSWORD = "docker";

    private static final LoadingCache<DatabaseConfig, DatabaseInstance> databases = CacheBuilder.newBuilder()
            .build(new CacheLoader<DatabaseConfig, DatabaseInstance>() {
                public DatabaseInstance load(DatabaseConfig config) {
                    return new DatabaseInstance(config);
                }
            });

    private final DatabaseConfig databaseConfig;
    private final ClientConfig clientConfig;

    public DockerPostgresDatabaseProvider(Environment environment, ObjectProvider<List<PostgreSQLContainerCustomizer>> containerCustomizers) {
        String dockerImage = environment.getProperty("zonky.test.database.postgres.docker.image", "postgres:16-alpine");
        String tmpfsOptions = environment.getProperty("zonky.test.database.postgres.docker.tmpfs.options", "rw,noexec,nosuid");
        boolean tmpfsEnabled = environment.getProperty("zonky.test.database.postgres.docker.tmpfs.enabled", boolean.class, false);

        Map<String, String> initdbProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.initdb.properties");
        Map<String, String> configProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.server.properties");
        Map<String, String> connectProperties = PropertyUtils.extractAll(environment, "zonky.test.database.postgres.client.properties");

        List<PostgreSQLContainerCustomizer> customizers = Optional.ofNullable(containerCustomizers.getIfAvailable()).orElse(emptyList());

        this.databaseConfig = new DatabaseConfig(dockerImage, tmpfsOptions, tmpfsEnabled, initdbProperties, configProperties, customizers);
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
        DockerPostgresDatabaseProvider that = (DockerPostgresDatabaseProvider) o;
        return Objects.equals(databaseConfig, that.databaseConfig) &&
                Objects.equals(clientConfig, that.clientConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseConfig, clientConfig);
    }

    protected static class DatabaseInstance {

        private final PostgreSQLContainer container;
        private final Semaphore semaphore;

        private DatabaseInstance(DatabaseConfig config) {
            String initdbArgs = config.initdbProperties.entrySet().stream()
                    .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(" "));

            Map<String, String> serverProperties = new HashMap<>(config.configProperties);

            serverProperties.putIfAbsent("fsync", "off");
            serverProperties.putIfAbsent("full_page_writes", "off");
            serverProperties.putIfAbsent("max_connections", "300");

            String postgresArgs = serverProperties.entrySet().stream()
                    .map(e -> String.format("-c %s=%s", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(" "));

            container = createContainer(config.dockerImage, container -> {
                container.addEnv("POSTGRES_INITDB_ARGS", "--nosync " + initdbArgs);
                container.setCommand("postgres " + postgresArgs);
            });

            if (config.tmpfsEnabled) {
                Consumer<CreateContainerCmd> consumer = cmd -> cmd.getHostConfig()
                        .withTmpFs(ImmutableMap.of("/var/lib/postgresql/data", config.tmpfsOptions));
                container.withCreateContainerCmdModifier(consumer);
            }

            container.withUsername(DEFAULT_POSTGRES_USERNAME);
            container.withPassword(DEFAULT_POSTGRES_PASSWORD);

            config.customizers.forEach(c -> c.customize(container));

            container.start();
            container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger(DockerPostgresDatabaseProvider.class)));

            semaphore = new Semaphore(Integer.parseInt(serverProperties.get("max_connections")));
        }

        private PostgreSQLContainer createContainer(String dockerImage, Consumer<PostgreSQLContainer> configAction) {
            if (ClassUtils.hasMethod(DockerImageName.class, "asCompatibleSubstituteFor", String.class)) {
                return new PostgreSQLContainer(DockerImageName.parse(dockerImage).asCompatibleSubstituteFor("postgres")) {
                    @Override
                    protected void configure() {
                        super.configure();
                        configAction.accept(this);
                    }
                };
            } else {
                return new PostgreSQLContainer(dockerImage) {
                    @Override
                    protected void configure() {
                        super.configure();
                        configAction.accept(this);
                    }
                };
            }
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
                } catch (SQLException e) {
                    if ("55006".equals(e.getSQLState())) { // postgres error code for object_in_use condition
                        if (logger.isTraceEnabled()) {
                            logger.warn("Unable to release '{}' database", dbName, e);
                        } else {
                            logger.warn("Unable to release '{}' database", dbName);
                        }
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

            dataSource.setServerName(container.getContainerIpAddress());
            dataSource.setPortNumber(container.getMappedPort(POSTGRESQL_PORT));
            dataSource.setDatabaseName(dbName);

            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());

            for (Map.Entry<String, String> entry : config.connectProperties.entrySet()) {
                dataSource.setProperty(entry.getKey(), entry.getValue());
            }

            return new BlockingDatabaseWrapper(new PostgresEmbeddedDatabase(dataSource, () -> dropDatabase(config, dbName)), semaphore);
        }
    }

    private static class DatabaseConfig {

        private final String dockerImage;
        private final String tmpfsOptions;
        private final boolean tmpfsEnabled;
        private final Map<String, String> initdbProperties;
        private final Map<String, String> configProperties;
        private final List<PostgreSQLContainerCustomizer> customizers;

        private DatabaseConfig(String dockerImage, String tmpfsOptions, boolean tmpfsEnabled, Map<String, String> initdbProperties, Map<String, String> configProperties, List<PostgreSQLContainerCustomizer> customizers) {
            this.dockerImage = dockerImage;
            this.tmpfsOptions = tmpfsOptions;
            this.tmpfsEnabled = tmpfsEnabled;
            this.initdbProperties = ImmutableMap.copyOf(initdbProperties);
            this.configProperties = ImmutableMap.copyOf(configProperties);
            this.customizers = customizers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DatabaseConfig that = (DatabaseConfig) o;
            return tmpfsEnabled == that.tmpfsEnabled &&
                    Objects.equals(dockerImage, that.dockerImage) &&
                    Objects.equals(tmpfsOptions, that.tmpfsOptions) &&
                    Objects.equals(initdbProperties, that.initdbProperties) &&
                    Objects.equals(configProperties, that.configProperties) &&
                    DeepEquals.deepEquals(customizers, that.customizers);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(dockerImage, tmpfsOptions, tmpfsEnabled, initdbProperties, configProperties);
            result = 31 * result + DeepEquals.deepHashCode(customizers);
            return result;
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
