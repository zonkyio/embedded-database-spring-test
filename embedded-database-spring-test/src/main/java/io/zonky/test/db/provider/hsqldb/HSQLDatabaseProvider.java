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
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HSQLDatabaseProvider implements DatabaseProvider {

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        String databaseName = UUID.randomUUID().toString();

        dataSource.setDriverClass(org.hsqldb.jdbcDriver.class);
        dataSource.setUrl(String.format("jdbc:hsqldb:mem:%s", databaseName));
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        HSQLEmbeddedDatabase database = new HSQLEmbeddedDatabase(dataSource, databaseName,
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
        return Objects.hash(HSQLDatabaseProvider.class);
    }

    private static void shutdownDatabase(DataSource dataSource, String dbName) {
        CompletableFuture.runAsync(() -> {
            try {
                executeStatement(dataSource, "SHUTDOWN");
            } catch (SQLException e) {
                // nothing to do
            }
        });
    }

    private static void executeStatement(DataSource dataSource, String ddlStatement) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(ddlStatement);
        }
    }
}
