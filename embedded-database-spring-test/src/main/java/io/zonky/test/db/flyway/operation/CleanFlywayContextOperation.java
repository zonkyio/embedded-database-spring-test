package io.zonky.test.db.flyway.operation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.flywaydb.core.Flyway;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import io.zonky.test.db.flyway.DataSourceContext;
import io.zonky.test.db.flyway.preparer.CleanFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.FlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.MigrateFlywayDatabasePreparer;
import io.zonky.test.db.provider.DatabasePreparer;

import static java.util.stream.Collectors.toList;

public class CleanFlywayContextOperation extends FlywayContextOperation {

    public CleanFlywayContextOperation(Flyway flyway) {
        super(flyway);
    }

    @Override
    public DataSourceContext.DatabasePrescription apply(DataSourceContext context) {
        List<DataSourceContext.DatabaseSnapshot> snapshots = context.getSnapshots();
        DataSourceContext.DatabasePrescription descriptor = Iterables.getLast(snapshots).getDescriptor();
        List<DatabasePreparer> allPreparers = descriptor.getPreparers();

        List<DatabasePreparer> filteredPreparers = allPreparers.stream()
                .filter(isMigratePreparerWithConfig(getFlyway()).negate())
                .collect(toList());

        boolean sharedSchemas = filteredPreparers.stream()
                .anyMatch(isFlywayPreparerWithSchema(getFlyway().getSchemas()));

        if (!sharedSchemas && filteredPreparers.size() == allPreparers.size() - 1) {
            return new DataSourceContext.DatabasePrescription(filteredPreparers);
        }

        FlywayDatabasePreparer cleanPreparer = new CleanFlywayDatabasePreparer(getFlyway());
        List<DatabasePreparer> newPreparers = ImmutableList.<DatabasePreparer>builder()
                .addAll(allPreparers)
                .add(cleanPreparer)
                .build();

        return new DataSourceContext.DatabasePrescription(newPreparers);
    }

    private Predicate<DatabasePreparer> isFlywayPreparerWithSchema(String[] schemas) {
        Set<String> schemaSet = ImmutableSet.copyOf(schemas);
        return preparer -> preparer instanceof FlywayDatabasePreparer
                && Arrays.stream(((FlywayDatabasePreparer) preparer).getFlyway().getSchemas()).anyMatch(schemaSet::contains);
    }

    private Predicate<DatabasePreparer> isMigratePreparerWithConfig(Flyway flyway) {
        return preparer -> preparer instanceof MigrateFlywayDatabasePreparer && ((MigrateFlywayDatabasePreparer) preparer).getFlyway() == flyway;
    }
}
