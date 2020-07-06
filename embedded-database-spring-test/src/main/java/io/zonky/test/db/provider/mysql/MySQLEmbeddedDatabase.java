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

import com.mysql.cj.jdbc.MysqlDataSource;
import io.zonky.test.db.provider.AbstractEmbeddedDatabase;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;

public class MySQLEmbeddedDatabase extends AbstractEmbeddedDatabase {

    private final MysqlDataSource dataSource;

    public MySQLEmbeddedDatabase(MysqlDataSource dataSource, Runnable closeCallback) {
        super(closeCallback);
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public String getUrl() {
        String url = dataSource.getUrl() + String.format("?user=%s", dataSource.getUser());
        if (StringUtils.isNotBlank(dataSource.getPassword())) {
            url += String.format("&password=%s", dataSource.getPassword());
        }
        return url;
    }
}
