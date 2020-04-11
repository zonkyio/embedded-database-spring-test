package io.zonky.test.db.provider;

import io.zonky.test.db.preparer.DatabasePreparer;

import java.util.Objects;

public class DatabaseRequest {

    private final DatabasePreparer preparer;
    private final DatabaseTemplate template;

    public static DatabaseRequest of(DatabasePreparer preparer) {
        return new DatabaseRequest(preparer, null);
    }

    public static DatabaseRequest of(DatabasePreparer preparer, DatabaseTemplate template) {
        return new DatabaseRequest(preparer, template);
    }

    private DatabaseRequest(DatabasePreparer preparer, DatabaseTemplate template) {
        this.template = template;
        this.preparer = preparer;
    }

    public DatabasePreparer getPreparer() {
        return preparer;
    }

    public DatabaseTemplate getTemplate() {
        return template;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseRequest that = (DatabaseRequest) o;
        return Objects.equals(preparer, that.preparer) &&
                Objects.equals(template, that.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preparer, template);
    }
}
