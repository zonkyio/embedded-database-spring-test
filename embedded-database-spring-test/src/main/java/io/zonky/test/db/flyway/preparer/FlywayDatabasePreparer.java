package io.zonky.test.db.flyway.preparer;

import io.zonky.test.db.flyway.FlywayDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.Objects;

public abstract class FlywayDatabasePreparer implements DatabasePreparer {

    private final FlywayDescriptor descriptor;

    public FlywayDatabasePreparer(FlywayDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public FlywayDescriptor getFlywayDescriptor() {
        return descriptor;
    }

    protected abstract void doOperation(Flyway flyway);

    @Override
    public void prepare(DataSource ds) {
        Flyway flyway = new Flyway();

        descriptor.applyTo(flyway);
        flyway.setDataSource(ds);

        doOperation(flyway);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlywayDatabasePreparer that = (FlywayDatabasePreparer) o;
        return Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor);
    }
}
