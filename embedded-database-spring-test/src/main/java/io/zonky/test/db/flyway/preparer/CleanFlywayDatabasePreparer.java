package io.zonky.test.db.flyway.preparer;

import io.zonky.test.db.flyway.FlywayDescriptor;
import org.flywaydb.core.Flyway;

public class CleanFlywayDatabasePreparer extends FlywayDatabasePreparer {

    public CleanFlywayDatabasePreparer(FlywayDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    protected void doOperation(Flyway flyway) {
        flyway.clean();
    }
}
