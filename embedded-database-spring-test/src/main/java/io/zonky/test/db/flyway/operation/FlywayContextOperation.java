package io.zonky.test.db.flyway.operation;

import org.flywaydb.core.Flyway;

import io.zonky.test.db.flyway.DataSourceContext;

public abstract class FlywayContextOperation implements DataSourceContext.ContextOperation {

    private final Flyway flyway;

    public FlywayContextOperation(Flyway flyway) {
        this.flyway = flyway;
    }

    public Flyway getFlyway() {
        return flyway;
    }
}
