package io.zonky.test.db.flyway.preparer;

import io.zonky.test.db.flyway.FlywayConfigSnapshot;
import io.zonky.test.db.provider.DatabasePreparer;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.Objects;

public abstract class FlywayDatabasePreparer implements DatabasePreparer {

    private final FlywayConfigSnapshot configSnapshot;
    private final Flyway flyway;

    public FlywayDatabasePreparer(Flyway flyway) {
        this.configSnapshot = new FlywayConfigSnapshot(flyway);
        this.flyway = flyway;
    }

    public FlywayConfigSnapshot getConfigSnapshot() {
        return configSnapshot;
    }

    public Flyway getFlyway() {
        return flyway;
    }

    protected abstract void doOperation(Flyway flyway);

    @Override
    public void prepare(DataSource ds) {
        DataSource dataSource = flyway.getDataSource();
        String[] locations = flyway.getLocations(); // TODO: this is because the flyway bean is mutable
        String[] schemas = flyway.getSchemas(); // TODO: different flyway versions use different schema types
        try {
            System.out.println("XXX - migration started");
            flyway.setDataSource(ds);
            flyway.setLocations(configSnapshot.getLocations().stream().map(o -> ((String) o)).toArray(String[]::new));
            doOperation(flyway); // TODO: use a new local flyway instance for each execution to prevent potential concurrency issues
            System.out.println("XXX - migration finished");
        } finally {
            flyway.setDataSource(dataSource);
            flyway.setLocations(locations);
            flyway.setSchemas(schemas);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlywayDatabasePreparer that = (FlywayDatabasePreparer) o;
        return Objects.equals(configSnapshot, that.configSnapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configSnapshot);
    }
}
