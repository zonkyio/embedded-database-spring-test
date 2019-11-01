package io.zonky.test.db.provider;

public class DatabaseRequest {

    private final DatabaseTemplate template;
    private final DatabasePreparer preparer;

    public DatabaseRequest(DatabasePreparer preparer) {
        this.template = null;
        this.preparer = preparer;
    }

    public DatabaseRequest(DatabaseTemplate template, DatabasePreparer preparer) {
        this.template = template;
        this.preparer = preparer;
    }

    public DatabaseTemplate getTemplate() {
        return template;
    }

    public DatabasePreparer getPreparer() {
        return preparer;
    }
}
