package io.zonky.test.db.flyway.preparer;

import org.flywaydb.core.Flyway;

public class CleanFlywayDatabasePreparer extends FlywayDatabasePreparer {

    public CleanFlywayDatabasePreparer(Flyway flyway) {
        super(flyway);
    }

    @Override
    protected void doOperation(Flyway flyway) {
        flyway.clean();
    }
}
