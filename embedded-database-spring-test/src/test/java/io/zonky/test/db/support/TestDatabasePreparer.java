package io.zonky.test.db.support;

import io.zonky.test.db.preparer.DatabasePreparer;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Consumer;

public class TestDatabasePreparer implements DatabasePreparer {

    public static TestDatabasePreparer empty() {
        return new TestDatabasePreparer(null, dataSource -> {});
    }

    public static TestDatabasePreparer empty(String identifier) {
        return new TestDatabasePreparer(identifier, dataSource -> {});
    }

    public static TestDatabasePreparer of(Consumer<DataSource> action) {
        return new TestDatabasePreparer(null, action);
    }

    public static TestDatabasePreparer of(String identifier, Consumer<DataSource> action) {
        return new TestDatabasePreparer(identifier, action);
    }

    public static TestDatabasePreparer of(String identifier, long duration, Consumer<DataSource> action) {
        return new TestDatabasePreparer(identifier, action, duration);
    }

    private final String identifier;
    private final Consumer<DataSource> action;
    private final long estimatedDuration;

    private TestDatabasePreparer(String identifier, Consumer<DataSource> action) {
        this.identifier = identifier;
        this.action = action;
        this.estimatedDuration = 0;
    }

    private TestDatabasePreparer(String identifier, Consumer<DataSource> action, long estimatedDuration) {
        this.identifier = identifier;
        this.action = action;
        this.estimatedDuration = estimatedDuration;
    }

    @Override
    public long estimatedDuration() {
        return estimatedDuration;
    }

    @Override
    public void prepare(DataSource dataSource) {
        action.accept(dataSource);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestDatabasePreparer that = (TestDatabasePreparer) o;
        if (identifier == null || that.identifier == null) return false;
        return Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }
}
