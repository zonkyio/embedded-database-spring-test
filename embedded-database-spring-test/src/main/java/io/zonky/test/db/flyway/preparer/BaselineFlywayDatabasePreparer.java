package io.zonky.test.db.flyway.preparer;

import org.flywaydb.core.Flyway;

public class BaselineFlywayDatabasePreparer extends FlywayDatabasePreparer {

    public BaselineFlywayDatabasePreparer(Flyway flyway) {
        super(flyway);
    }

    @Override
    protected void doOperation(Flyway flyway) {
        flyway.baseline();
    }
}
