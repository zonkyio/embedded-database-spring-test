package io.zonky.test.db.flyway.operation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.flywaydb.core.Flyway;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.List;

import io.zonky.test.db.flyway.DataSourceContext;
import io.zonky.test.db.flyway.preparer.FlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.MigrateFlywayDatabasePreparer;
import io.zonky.test.db.provider.DatabasePreparer;

import static java.util.stream.Collectors.toList;

public class MigrateFlywayContextOperation extends FlywayContextOperation {

    private final SettableListenableFuture<Integer> result = new SettableListenableFuture<>();

    public MigrateFlywayContextOperation(Flyway flyway) {
        super(flyway);
    }

    public ListenableFuture<Integer> getResult() {
        return result;
    }

    @Override
    public DataSourceContext.DatabasePrescription apply(DataSourceContext context) {
        List<DataSourceContext.DatabaseSnapshot> snapshots = context.getSnapshots();
        DataSourceContext.DatabaseSnapshot snapshot = Iterables.getLast(snapshots);
        DataSourceContext.ContextOperation operation = snapshot.getOperation();

        if (operation instanceof CleanFlywayContextOperation) {
            CleanFlywayContextOperation cleanOperation = (CleanFlywayContextOperation) operation;
            if (cleanOperation.getFlyway() == this.getFlyway() && snapshots.size() > 1
                    && snapshots.get(snapshots.size() - 2).getDescriptor().getPreparers().size() > snapshots.get(snapshots.size() - 1).getDescriptor().getPreparers().size()) {
                DataSourceContext.DatabaseSnapshot beforeCleanSnapshot = snapshots.get(snapshots.size() - 2);

                // TODO: simplify
                List<DatabasePreparer> newPreparers = beforeCleanSnapshot.getDescriptor().getPreparers().stream()
                        .map(preparer -> {
                            if (!(preparer instanceof FlywayDatabasePreparer)) {
                                return preparer;
                            }
                            FlywayDatabasePreparer flywayPreparer = (FlywayDatabasePreparer) preparer;
                            if (!(flywayPreparer instanceof MigrateFlywayDatabasePreparer) || flywayPreparer.getFlyway() != this.getFlyway()) {
                                return preparer;
                            }
                            // the preparer must be replaced by a new instance because of the possibility of different flyway configuration
                            return createMigrateFlywayDatabasePreparer(this.getFlyway());
                        })
                        .collect(toList());

                return new DataSourceContext.DatabasePrescription(newPreparers);
            }
        }

        DatabasePreparer migratePreparer = createMigrateFlywayDatabasePreparer(this.getFlyway());
        List<DatabasePreparer> newPreparers = ImmutableList.<DatabasePreparer>builder()
                .addAll(snapshot.getDescriptor().getPreparers())
                .add(migratePreparer)
                .build();

        return new DataSourceContext.DatabasePrescription(newPreparers);
    }

    private DatabasePreparer createMigrateFlywayDatabasePreparer(Flyway flyway) {
        MigrateFlywayDatabasePreparer preparer = new MigrateFlywayDatabasePreparer(flyway);
        ListenableFuture<Integer> preparerResult = preparer.getResult();
        preparerResult.addCallback(result::set, result::setException); // TODO
        return preparer;
    }
}
