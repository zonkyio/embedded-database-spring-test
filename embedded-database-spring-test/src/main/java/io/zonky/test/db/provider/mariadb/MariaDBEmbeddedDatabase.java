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

package io.zonky.test.db.provider.mariadb;

import io.zonky.test.db.provider.support.AbstractEmbeddedDatabase;
import org.apache.commons.lang3.StringUtils;
import org.mariadb.jdbc.MariaDbDataSource;

import javax.sql.DataSource;

import static io.zonky.test.db.util.ReflectionUtils.getField;

public class MariaDBEmbeddedDatabase extends AbstractEmbeddedDatabase {

    private final MariaDbDataSource dataSource;

    public MariaDBEmbeddedDatabase(MariaDbDataSource dataSource, Runnable closeCallback) {
        super(closeCallback);
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public String getUrl() {
        String url = String.format("jdbc:mariadb://%s:%s/%s?user=%s",
                dataSource.getServerName(), dataSource.getPort(), dataSource.getDatabaseName(), dataSource.getUser());
        if (StringUtils.isNotBlank(getPassword())) {
            url += String.format("&password=%s", getPassword());
        }
        return url;
    }

    private String getPassword() {
        return getField(dataSource, "password");
    }
}
