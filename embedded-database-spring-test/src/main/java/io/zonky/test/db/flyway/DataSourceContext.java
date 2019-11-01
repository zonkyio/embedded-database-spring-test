package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import org.springframework.aop.TargetSource;

import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

// TODO: move into a different package
public interface DataSourceContext extends TargetSource {

    void setDescriptor(DatabaseDescriptor descriptor);

    List<DatabaseSnapshot> getSnapshots();

    DatabaseSnapshot apply(ContextOperation operation);

    boolean isInitialized();

    class DatabaseSnapshot {

        private final ContextOperation operation;
        private final DatabasePrescription descriptor;

        public DatabaseSnapshot(ContextOperation operation, DatabasePrescription descriptor) {
            this.operation = operation;
            this.descriptor = descriptor;
        }

        public ContextOperation getOperation() {
            return operation;
        }

        public DatabasePrescription getDescriptor() {
            return descriptor;
        }
    }

    interface ContextOperation {

        DatabasePrescription apply(DataSourceContext context);

    }

    class DatabasePrescription {

        private final List<DatabasePreparer> preparers;

        public DatabasePrescription(List<DatabasePreparer> preparers) {
            this.preparers = ImmutableList.copyOf(preparers);
        }

        public List<DatabasePreparer> getPreparers() {
            return preparers;
        }
    }

    class TagOperation implements ContextOperation {

        private final Object event;

        public TagOperation(Object event) {
            this.event = event;
        }

        public Object event() {
            return event;
        }

        @Override
        public DatabasePrescription apply(DataSourceContext context) {
            List<DatabaseSnapshot> snapshots = context.getSnapshots();
            if (snapshots.isEmpty()) {
                return new DatabasePrescription(Collections.emptyList());
            } else {
                return Iterables.getLast(snapshots).getDescriptor();
            }
        }
    }

    class ResetOperation implements ContextOperation {

        private final DatabaseSnapshot snapshot;

        public ResetOperation(DatabaseSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public DatabasePrescription apply(DataSourceContext context) {
            List<DatabaseSnapshot> snapshots = context.getSnapshots();
            checkState(snapshots.contains(snapshot), "specified snapshot was not found"); // TODO
            return snapshot.getDescriptor();
        }
    }

    class PreparerOperation implements ContextOperation {

        private final DatabasePreparer preparer;

        public PreparerOperation(DatabasePreparer preparer) {
            this.preparer = preparer;
        }

        public DatabasePreparer getPreparer() {
            return preparer;
        }

        @Override
        public DatabasePrescription apply(DataSourceContext context) {
            List<DatabaseSnapshot> snapshots = context.getSnapshots();
            DatabasePrescription descriptor = Iterables.getLast(snapshots).getDescriptor();

            List<DatabasePreparer> preparers = ImmutableList.<DatabasePreparer>builder()
                    .addAll(descriptor.getPreparers())
                    .add(preparer)
                    .build();

            return new DatabasePrescription(preparers);
        }
    }
}
