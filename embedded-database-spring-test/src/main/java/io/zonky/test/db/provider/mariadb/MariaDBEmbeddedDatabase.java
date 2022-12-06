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
import org.mariadb.jdbc.MariaDbDataSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.List;

import static io.zonky.test.db.util.ReflectionUtils.getField;
import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;

public class MariaDBEmbeddedDatabase extends AbstractEmbeddedDatabase {

    private final MariaDbDataSource dataSource;
    private final Object conf;

    public MariaDBEmbeddedDatabase(MariaDbDataSource dataSource, Runnable closeCallback) {
        super(closeCallback);
        this.dataSource = dataSource;

        if (ClassUtils.hasMethod(MariaDbDataSource.class, "getUrl")) {
            conf = getField(dataSource, "conf");
        } else {
            conf = null;
        }
    }

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public String getJdbcUrl() {
        String url = String.format("jdbc:mariadb://%s:%s/%s?user=%s",
                getServerName(), getPortNumber(), getDatabaseName(), dataSource.getUser());
        if (StringUtils.hasText(getPassword())) {
            url += String.format("&password=%s", getPassword());
        }
        return url;
    }

    public String getServerName() {
        if (conf != null) {
            return getField(getHostAddress(), "host");
        } else {
            return invokeMethod(dataSource, "getServerName");
        }
    }

    public int getPortNumber() {
        if (conf != null) {
            return getField(getHostAddress(), "port");
        } else {
            return invokeMethod(dataSource, "getPortNumber");
        }
    }

    public String getDatabaseName() {
        if (conf != null) {
            return getField(conf, "database");
        } else {
            return invokeMethod(dataSource, "getDatabaseName");
        }
    }

    private String getPassword() {
        return getField(dataSource, "password");
    }

    private Object getHostAddress() {
        List<?> addresses = invokeMethod(conf, "addresses");
        if (addresses != null && addresses.size() > 0) {
            return addresses.get(0);
        }
        throw new IllegalStateException("Missing host address");
    }
}
