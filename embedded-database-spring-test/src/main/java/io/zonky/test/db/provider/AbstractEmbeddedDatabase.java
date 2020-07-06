package io.zonky.test.db.provider;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

public abstract class AbstractEmbeddedDatabase implements EmbeddedDatabase {

    private final Runnable closeCallback;

    protected AbstractEmbeddedDatabase(Runnable closeCallback) {
        this.closeCallback = closeCallback;
    }

    protected abstract DataSource getDataSource();

    @Override
    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getDataSource().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return getDataSource().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        getDataSource().setLogWriter(out);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return getDataSource().getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        getDataSource().setLoginTimeout(seconds);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        if (iface.isAssignableFrom(getDataSource().getClass())) {
            return iface.cast(getDataSource());
        }
        return getDataSource().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        }
        if (iface.isAssignableFrom(getDataSource().getClass())) {
            return true;
        }
        return getDataSource().isWrapperFor(iface);
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return getDataSource().getParentLogger();
    }

    @Override
    public synchronized void close() {
        closeCallback.run();
    }
}
