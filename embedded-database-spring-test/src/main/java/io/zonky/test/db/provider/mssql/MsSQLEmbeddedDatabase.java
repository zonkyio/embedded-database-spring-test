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

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import io.zonky.test.db.provider.EmbeddedDatabase;
import org.apache.commons.lang3.StringUtils;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;

public class MsSQLEmbeddedDatabase implements EmbeddedDatabase {

    private final SQLServerDataSource dataSource;
    private final CloseCallback closeCallback;

    public MsSQLEmbeddedDatabase(SQLServerDataSource dataSource, CloseCallback closeCallback) {
        this.dataSource = dataSource;
        this.closeCallback = closeCallback;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return dataSource.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() {
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        dataSource.setLogWriter(out);
    }

    @Override
    public int getLoginTimeout() {
        return dataSource.getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(int seconds) {
        dataSource.setLoginTimeout(seconds);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        return dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        }
        return dataSource.isWrapperFor(iface);
    }
    
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return dataSource.getParentLogger();
    }

    @Override
    public String getUrl() {
        String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;user=%s",
                dataSource.getServerName(), dataSource.getPortNumber(), dataSource.getDatabaseName(), dataSource.getUser());
        if (StringUtils.isNotBlank(getPassword())) {
            url += String.format(";password=%s", getPassword());
        }
        return url;
    }

    private String getPassword() {
        return invokeMethod(dataSource, "getPassword");
    }

    @Override
    public Map<String, String> getAliases() {
        return Collections.emptyMap();
    }

    @Override
    public synchronized void close() {
        try {
            closeCallback.call();
        } catch (SQLException e) {
            // TODO: investigate the issue and consider adding a configuration property for enabling/disabling the exception
//            throw new ProviderException("Unexpected error when releasing the database", e);
        }
    }

    @FunctionalInterface
    public interface CloseCallback {

        void call() throws SQLException;

    }
}
