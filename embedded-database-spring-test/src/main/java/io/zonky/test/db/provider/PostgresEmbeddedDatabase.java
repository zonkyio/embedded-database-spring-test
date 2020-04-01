package io.zonky.test.db.provider;

import org.apache.commons.lang3.StringUtils;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class PostgresEmbeddedDatabase implements EmbeddedDatabase {

    private final PGSimpleDataSource dataSource;
    private final CloseCallback closeCallback;

    public PostgresEmbeddedDatabase(PGSimpleDataSource dataSource, CloseCallback closeCallback) {
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
        if (EmbeddedDatabase.class.isAssignableFrom(iface) || PostgresEmbeddedDatabase.class.isAssignableFrom(iface)) {
            return (T) this;
        }
        return dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return dataSource.isWrapperFor(iface);
    }
    
    @Override
    public Logger getParentLogger() {
        return dataSource.getParentLogger();
    }

    @Override
    public String getUrl() {
        String url = dataSource.getUrl() + String.format("?user=%s", getUser());

        if (StringUtils.isNotBlank(getPassword())) {
            url += String.format("&password=%s", getPassword());
        }

        return url;
    }

    @Override
    public String getServerName() {
        return dataSource.getServerName();
    }

    @Override
    public String getDatabaseName() {
        return dataSource.getDatabaseName();
    }

    @Override
    public String getUser() {
        return dataSource.getUser();
    }

    @Override
    public String getPassword() {
        return dataSource.getPassword();
    }

    @Override
    public int getPortNumber() {
        return dataSource.getPortNumber();
    }

    @Override
    public Map<String, String> getAliases() {
        return Collections.emptyMap();
    }

    @Override
    public synchronized void close() throws SQLException {
        closeCallback.call();
    }

    @FunctionalInterface
    public interface CloseCallback {

        void call() throws SQLException;

    }
}
