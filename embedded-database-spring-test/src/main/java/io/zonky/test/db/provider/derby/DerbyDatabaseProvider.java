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

package io.zonky.test.db.provider.derby;

import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DerbyDatabaseProvider implements DatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(DerbyDatabaseProvider.class);
    private static final String URL_TEMPLATE = "jdbc:derby:memory:%s";

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        String databaseName = UUID.randomUUID().toString();

        dataSource.setDriverClass(org.h2.Driver.class);
        dataSource.setUrl(String.format(URL_TEMPLATE + ";drop=true", databaseName));
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        DerbyEmbeddedDatabase database = new DerbyEmbeddedDatabase(dataSource, databaseName,
                () -> shutdownDatabase(databaseName));
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
        return Objects.hash(DerbyDatabaseProvider.class);
    }

    private static void shutdownDatabase(String dbName) {
        CompletableFuture.runAsync(() -> {
            try {
                DriverManager.getConnection(String.format(URL_TEMPLATE + ";shutdown=true", dbName));
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
}
