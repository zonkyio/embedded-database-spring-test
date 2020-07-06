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

package io.zonky.test.db.provider.mysql;

import com.cedarsoftware.util.DeepEquals;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.mysql.cj.jdbc.MysqlDataSource;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.BlockingDatabaseWrapper;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import io.zonky.test.db.provider.mysql.MySQLEmbeddedDatabase.CloseCallback;
import io.zonky.test.db.util.PropertyUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static org.testcontainers.containers.MySQLContainer.MYSQL_PORT;

public class DockerMySQLDatabaseProvider implements DatabaseProvider {

    private static final String DEFAULT_MYSQL_USERNAME = "test";
    private static final String DEFAULT_MYSQL_PASSWORD = "docker";

    private static final LoadingCache<DatabaseConfig, DatabasePool> databasesPools = CacheBuilder.newBuilder()
            .build(new CacheLoader<DatabaseConfig, DatabasePool>() {
                public DatabasePool load(DatabaseConfig config) {
                    return new DatabasePool(config);
                }
            });

    private final DatabaseConfig databaseConfig;
    private final ClientConfig clientConfig;

    public DockerMySQLDatabaseProvider(Environment environment, ObjectProvider<List<MySQLContainerCustomizer>> containerCustomizers) {
        String dockerImage = environment.getProperty("zonky.test.database.mysql.docker.image", "mysql:5.7");
        String tmpfsOptions = environment.getProperty("zonky.test.database.mysql.docker.tmpfs.options", "rw,noexec,nosuid");
        boolean tmpfsEnabled = environment.getProperty("zonky.test.database.mysql.docker.tmpfs.enabled", boolean.class, false);

        Map<String, String> connectProperties = PropertyUtils.extractAll(environment, "zonky.test.database.mysql.client.properties");

        List<MySQLContainerCustomizer> customizers = Optional.ofNullable(containerCustomizers.getIfAvailable()).orElse(emptyList());

        this.databaseConfig = new DatabaseConfig(dockerImage, tmpfsOptions, tmpfsEnabled, customizers);
        this.clientConfig = new ClientConfig(connectProperties);
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
        try {
            DatabasePool pool = databasesPools.get(databaseConfig);
            return pool.createDatabase(clientConfig, preparer);
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
        DockerMySQLDatabaseProvider that = (DockerMySQLDatabaseProvider) o;
        return Objects.equals(databaseConfig, that.databaseConfig) &&
                Objects.equals(clientConfig, that.clientConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseConfig, clientConfig);
    }

    protected static class DatabasePool {

        private final BlockingQueue<DatabaseInstance> databases = new LinkedBlockingQueue<>();
        private final DatabaseConfig databaseConfig;

        private DatabasePool(DatabaseConfig config) {
            this.databaseConfig = config;
        }

        public EmbeddedDatabase createDatabase(ClientConfig config, DatabasePreparer preparer) throws SQLException {
            DatabaseInstance instance = databases.poll();

            if (instance == null) {
                instance = new DatabaseInstance(databaseConfig);
            }

            try {
                EmbeddedDatabase database = instance.createDatabase(config, preparer);
                database.unwrap(MySQLEmbeddedDatabase.class).registerCloseCallback(releaseDatabase(instance));
                return database;
            } catch (Exception e) {
                databases.offer(instance); // TODO: implement it to other providers too
                throw e;
            }
        }

        private CloseCallback releaseDatabase(DatabaseInstance instance) {
            return () -> databases.offer(instance);
        }
    }

    protected static class DatabaseInstance {

        private final MySQLContainer container;
        private final Semaphore semaphore;

        private DatabaseInstance(DatabaseConfig config) {
            container = new MySQLContainer(config.dockerImage);

            if (config.tmpfsEnabled) {
                Consumer<CreateContainerCmd> consumer = cmd -> cmd.getHostConfig()
                        .withTmpFs(ImmutableMap.of("/var/lib/mysql", config.tmpfsOptions));
                container.withCreateContainerCmdModifier(consumer);
            }

            container.withUsername(DEFAULT_MYSQL_USERNAME);
            container.withPassword(DEFAULT_MYSQL_PASSWORD);

            config.customizers.forEach(c -> c.customize(container));

            container.start();
            container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger(DockerMySQLDatabaseProvider.class)));

            semaphore = new Semaphore(150);
        }

        public EmbeddedDatabase createDatabase(ClientConfig config, DatabasePreparer preparer) throws SQLException {
            String databaseName = container.getDatabaseName();
            executeStatement(config, String.format("CREATE DATABASE IF NOT EXISTS %s", databaseName));
            try {
                EmbeddedDatabase database = getDatabase(config, databaseName);
                if (preparer != null) {
                    preparer.prepare(database);
                }
                return database;
            } catch (Exception e) {
                dropDatabase(config, databaseName); // TODO: implement it to other providers too
                throw e;
            }
        }

        private void dropDatabase(ClientConfig config, String dbName) {
            try {
                String dropCommand = "mysql -uroot -pdocker -N -e \"show databases\" | grep -v -E \"^(information_schema|performance_schema|mysql|sys)$\" | awk '{print \"drop database \" $1 \"\"}' | mysql -uroot -pdocker";
                ExecResult dropResult = container.execInContainer("sh", "-c", dropCommand);
                if (dropResult.getExitCode() != 0) {
                    throw new ProviderException("Unexpected error when cleaning up the database");
                }
            } catch (Exception e) {
                Throwables.throwIfInstanceOf(e.getCause(), ProviderException.class);
                throw new ProviderException("Unexpected error when cleaning up the database", e.getCause());
            }
        }

        private void executeStatement(ClientConfig config, String ddlStatement) throws SQLException {
            DataSource dataSource = getDatabase(config, "mysql");
            try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(ddlStatement)) {
                stmt.execute();
            }
        }

        private EmbeddedDatabase getDatabase(ClientConfig config, String dbName) {
            MysqlDataSource dataSource = new MysqlDataSource();

            dataSource.setServerName(container.getContainerIpAddress());
            dataSource.setPortNumber(container.getMappedPort(MYSQL_PORT));
            dataSource.setDatabaseName(dbName);

            if ("mysql".equals(dbName)) {
                dataSource.setUser("root");
            } else {
                dataSource.setUser(container.getUsername());
            }
            dataSource.setPassword(container.getPassword());

            BeanWrapper dataSourceWrapper = new BeanWrapperImpl(dataSource);
            for (Map.Entry<String, String> entry : config.connectProperties.entrySet()) {
                dataSourceWrapper.setPropertyValue(entry.getKey(), entry.getValue());
            }

            return new BlockingDatabaseWrapper(new MySQLEmbeddedDatabase(dataSource, () -> dropDatabase(config, dbName)), semaphore);
        }
    }

    private static class DatabaseConfig {

        private final String dockerImage;
        private final String tmpfsOptions;
        private final boolean tmpfsEnabled;
        private final List<MySQLContainerCustomizer> customizers;

        private DatabaseConfig(String dockerImage, String tmpfsOptions, boolean tmpfsEnabled, List<MySQLContainerCustomizer> customizers) {
            this.dockerImage = dockerImage;
            this.tmpfsOptions = tmpfsOptions;
            this.tmpfsEnabled = tmpfsEnabled;
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
                    DeepEquals.deepEquals(customizers, that.customizers);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(dockerImage, tmpfsOptions, tmpfsEnabled);
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
