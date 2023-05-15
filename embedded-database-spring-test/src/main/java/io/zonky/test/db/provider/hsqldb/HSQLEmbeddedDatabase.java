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

import io.zonky.test.db.provider.support.AbstractEmbeddedDatabase;

import javax.sql.DataSource;

public class HSQLEmbeddedDatabase extends AbstractEmbeddedDatabase {

    private final DataSource dataSource;
    private final String dbName;

    public HSQLEmbeddedDatabase(DataSource dataSource, String dbName, Runnable closeCallback) {
        super(closeCallback);
        this.dataSource = dataSource;
        this.dbName = dbName;
    }

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public String getJdbcUrl() {
        return String.format("jdbc:hsqldb:mem:%s;user=sa", dbName);
    }

    public String getDatabaseName() {
        return dbName;
    }
}
