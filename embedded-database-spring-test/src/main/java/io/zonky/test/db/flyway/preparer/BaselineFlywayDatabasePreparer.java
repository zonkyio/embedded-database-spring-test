package io.zonky.test.db.flyway.preparer;

import io.zonky.test.db.flyway.FlywayDescriptor;
import org.flywaydb.core.Flyway;

public class BaselineFlywayDatabasePreparer extends FlywayDatabasePreparer {

    public BaselineFlywayDatabasePreparer(FlywayDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    protected void doOperation(Flyway flyway) {
        flyway.baseline();
    }
}
