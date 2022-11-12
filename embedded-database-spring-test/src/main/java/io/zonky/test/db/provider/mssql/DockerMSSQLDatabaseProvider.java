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

package io.zonky.test.db.provider.mssql;

import com.cedarsoftware.util.DeepEquals;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.microsoft.sqlserver.jdbc.ISQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import static java.util.Collections.emptyList;
import static org.testcontainers.containers.MSSQLServerContainer.MS_SQL_SERVER_PORT;

public class DockerMSSQLDatabaseProvider implements TemplatableDatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(DockerMSSQLDatabaseProvider.class);

    private static final LoadingCache<DatabaseConfig, DatabaseInstance> databases = CacheBuilder.newBuilder()
            .build(new CacheLoader<DatabaseConfig, DatabaseInstance>() {
                public DatabaseInstance load(DatabaseConfig config) {
                    return new DatabaseInstance(config);
                }
            });

    private final DatabaseConfig databaseConfig;
    private final ClientConfig clientConfig;

    public DockerMSSQLDatabaseProvider(Environment environment, ObjectProvider<List<MSSQLServerContainerCustomizer>> containerCustomizers) {
        String dockerImage = environment.getProperty("zonky.test.database.mssql.docker.image", "mcr.microsoft.com/mssql/server:2017-latest");
        Map<String, String> connectProperties = PropertyUtils.extractAll(environment, "zonky.test.database.mssql.client.properties");
        List<MSSQLServerContainerCustomizer> customizers = Optional.ofNullable(containerCustomizers.getIfAvailable()).orElse(emptyList());

        this.databaseConfig = new DatabaseConfig(dockerImage, customizers);
        this.clientConfig = new ClientConfig(connectProperties);
    }

    @Override
    public DatabaseTemplate createTemplate(DatabaseRequest request) throws ProviderException {
        try {
            DatabaseInstance instance = databases.get(databaseConfig);
            return instance.createTemplate(clientConfig, request);
        } catch (ExecutionException | UncheckedExecutionException e) {
            Throwables.throwIfInstanceOf(e.getCause(), ProviderException.class);
            throw new ProviderException("Unexpected error when preparing a database cluster", e.getCause());
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
        DockerMSSQLDatabaseProvider that = (DockerMSSQLDatabaseProvider) o;
        return Objects.equals(databaseConfig, that.databaseConfig) &&
                Objects.equals(clientConfig, that.clientConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseConfig, clientConfig);
    }

    protected static class DatabaseInstance {

        private final MSSQLServerContainer container;
        private final Semaphore semaphore;

        private DatabaseInstance(DatabaseConfig config) {
            container = createContainer(config.dockerImage);
            config.customizers.forEach(c -> c.customize(container));

            container.start();
            container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger(DockerMSSQLDatabaseProvider.class)));

            semaphore = new Semaphore(32767);
        }

        private MSSQLServerContainer createContainer(String dockerImage) {
            if (ClassUtils.hasMethod(DockerImageName.class, "asCompatibleSubstituteFor", String.class)) {
                return new MSSQLServerContainer(DockerImageName.parse(dockerImage).asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server")) {
                    @Override
                    public String getUsername() {
                        return "sa";
                    }
                };
            } else {
                return new MSSQLServerContainer(dockerImage) {
                    @Override
                    public String getUsername() {
                        return "sa";
                    }
                };
            }
        }

        public EmbeddedDatabase createDatabase(ClientConfig config, DatabaseRequest request) throws SQLException {
            DatabaseTemplate template = request.getTemplate();
            DatabasePreparer preparer = request.getPreparer();

            String databaseName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);

            if (template != null) {
                executeStatement(config, String.format("RESTORE DATABASE %s FROM DISK = N'/var/opt/mssql/template/%s.bak' WITH MOVE '%s' TO N'/var/opt/mssql/data/%s.mdf', MOVE '%s_log' TO N'/var/opt/mssql/data/%s_log.ldf'",
                        databaseName, template.getTemplateName(), template.getTemplateName(), databaseName, template.getTemplateName(), databaseName));
            } else {
                executeStatement(config, String.format("CREATE DATABASE %s", databaseName));
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

        public DatabaseTemplate createTemplate(ClientConfig config, DatabaseRequest request) throws SQLException {
            try (EmbeddedDatabase database = createDatabase(config, request)) {
                ISQLServerDataSource dataSource = database.unwrap(ISQLServerDataSource.class);
                String templateName = dataSource.getDatabaseName();

                executeStatement(config, String.format("BACKUP DATABASE %s TO DISK = N'/var/opt/mssql/template/%s.bak'", templateName, templateName));
                return new SimpleDatabaseTemplate(templateName, () -> dropTemplate(templateName));
            }
        }

        private void dropDatabase(ClientConfig config, String dbName) {
            CompletableFuture.runAsync(() -> {
                try {
                    executeStatement(config, String.format("DROP DATABASE IF EXISTS %s", dbName));
                } catch (SQLException e) {
                    if (e.getErrorCode() == 3702) { // mssql server error code for database in use condition
                        if (logger.isTraceEnabled()) {
                            logger.warn("Unable to release '{}' database", dbName, e);
                        } else {
                            logger.warn("Unable to release '{}' database", dbName);
                        }
                    }
                }
            });
        }

        private void dropTemplate(String templateName) {
            CompletableFuture.runAsync(() -> {
                try {
                    container.execInContainer("rm", String.format("/var/opt/mssql/template/%s.bak", templateName));
                } catch (Exception e) {
                    logger.error("Unable to release '{}' database template", templateName, e);
                }
            });
        }

        private void executeStatement(ClientConfig config, String ddlStatement) throws SQLException {
            DataSource dataSource = getDatabase(config, "master");
            try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(ddlStatement)) {
                stmt.execute();
            }
        }

        private EmbeddedDatabase getDatabase(ClientConfig config, String dbName) {
            SQLServerDataSource dataSource = new SQLServerDataSource();

            dataSource.setServerName(container.getContainerIpAddress());
            dataSource.setPortNumber(container.getMappedPort(MS_SQL_SERVER_PORT));
            dataSource.setDatabaseName(dbName);

            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());

            dataSource.setTrustServerCertificate(true);

            BeanWrapper dataSourceWrapper = new BeanWrapperImpl(dataSource);
            for (Map.Entry<String, String> entry : config.connectProperties.entrySet()) {
                dataSourceWrapper.setPropertyValue(entry.getKey(), entry.getValue());
            }

            return new BlockingDatabaseWrapper(new MsSQLEmbeddedDatabase(dataSource, () -> dropDatabase(config, dbName)), semaphore);
        }
    }

    private static class DatabaseConfig {

        private final String dockerImage;
        private final List<MSSQLServerContainerCustomizer> customizers;

        private DatabaseConfig(String dockerImage, List<MSSQLServerContainerCustomizer> customizers) {
            this.dockerImage = dockerImage;
            this.customizers = customizers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DatabaseConfig that = (DatabaseConfig) o;
            return Objects.equals(dockerImage, that.dockerImage) &&
                    DeepEquals.deepEquals(customizers, that.customizers);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(dockerImage);
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
