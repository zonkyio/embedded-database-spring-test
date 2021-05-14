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

import io.zonky.test.db.provider.support.AbstractEmbeddedDatabase;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

public class PostgresEmbeddedDatabase extends AbstractEmbeddedDatabase {

    private final PGSimpleDataSource dataSource;

    public PostgresEmbeddedDatabase(PGSimpleDataSource dataSource, Runnable closeCallback) {
        super(closeCallback);
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public String getJdbcUrl() {
        String url = dataSource.getUrl() + String.format("?user=%s", dataSource.getUser());
        if (StringUtils.hasText(dataSource.getPassword())) {
            url += String.format("&password=%s", dataSource.getPassword());
        }
        return url;
    }
}
