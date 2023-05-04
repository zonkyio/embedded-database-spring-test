/*
 * Copyright 2021 the original author or authors.
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

package io.zonky.test.db.provider.hsqldb;

import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import io.zonky.test.db.util.ReflectionUtils;
import org.hsqldb.server.HsqlServerFactory;
import org.hsqldb.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HSQLDBDatabaseProvider implements DatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(HSQLDBDatabaseProvider.class);

    private static final Server server = startServer();

    private static Server startServer() {
        try {
            Server server = (Server) HsqlServerFactory.createHsqlServer("mem:testdb", true, false);
            server.start();
            registerShutdownHook(server);
            return server;
        } catch (SQLException e) {
            return null;
        }
    }

    private static void registerShutdownHook(Server server) {
        try {
            Class<?> applicationType = ClassUtils.forName("org.springframework.boot.SpringApplication", null);
            Object shutdownHandlers = ReflectionUtils.invokeStaticMethod(applicationType, "getShutdownHandlers");
            ReflectionUtils.invokeMethod(shutdownHandlers, "add", (Runnable) server::shutdown);
        } catch (Throwable ex) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        }
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        String databaseName = UUID.randomUUID().toString();

        dataSource.setDriverClass(org.h2.Driver.class);
        dataSource.setUrl(String.format("jdbc:hsqldb:mem:%s;DB_CLOSE_DELAY=-1", databaseName));
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        HSQLDBEmbeddedDatabase database = new HSQLDBEmbeddedDatabase(server, dataSource, databaseName,
                () -> shutdownDatabase(dataSource, databaseName));
        try {
            if (preparer != null) {
                preparer.prepare(database);
            }
        } catch (SQLException e) {
            throw new ProviderException("Unexpected error when creating a database", e);
        }
        return database;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash(HSQLDBDatabaseProvider.class);
    }

    private static void shutdownDatabase(DataSource dataSource, String dbName) {
        CompletableFuture.runAsync(() -> {
            try {
                executeStatement(dataSource, "SHUTDOWN");
            } catch (SQLException e) {
                // it seems that there is no error for database in use condition
                if (logger.isTraceEnabled()) {
                    logger.warn("Unable to release '{}' database", dbName, e);
                } else {
                    logger.warn("Unable to release '{}' database", dbName);
                }
            }
        });
    }

    private static void executeStatement(DataSource dataSource, String ddlStatement) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(ddlStatement);
        }
    }
}
