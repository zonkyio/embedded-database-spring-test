package io.zonky.test.db.flyway;

import io.zonky.test.db.logging.EmbeddedDatabaseReporter;
import io.zonky.test.db.preparer.RecordingDataSource;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.DatabaseResult;
import io.zonky.test.db.provider.config.DatabaseProviders;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class DefaultDataSourceContext implements DataSourceContext, ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private DatabaseProviders databaseProviders;

    protected DatabaseDescriptor databaseDescriptor;

    private final LinkedList<DatabaseSnapshot> snapshots;

    private DataSource dataSource;

    private boolean initialized = false;

    public DefaultDataSourceContext() {
        this.snapshots = new LinkedList<>();
//        apply(new TagOperation(null)); // TODO
        TagOperation tagOperation = new TagOperation(null);
        DatabasePrescription description = tagOperation.apply(this);
        DatabaseSnapshot snapshot = new DatabaseSnapshot(tagOperation, description);
        snapshots.add(snapshot);
    }

    @Override
    public Class<?> getTargetClass() {
        return DataSource.class;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public synchronized Object getTarget() throws Exception {
        if (initialized && dataSource == null) {
            refreshDatabase();
        }

        if (initialized || dataSource instanceof RecordingDataSource) {
            return dataSource;
        }

        dataSource = RecordingDataSource.wrap(dataSource);
        return dataSource;
    }

    @Override
    public void releaseTarget(Object target) {
        // nothing to do
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void setDescriptor(DatabaseDescriptor descriptor) {
        stopRecording();
        this.databaseDescriptor = descriptor;
        refreshDatabase();
    }

    @Override
    public List<DatabaseSnapshot> getSnapshots() {
        return Collections.unmodifiableList(snapshots);
    }

    private void stopRecording() {
        if (dataSource instanceof RecordingDataSource) {
            RecordingDataSource recordingDataSource = (RecordingDataSource) this.dataSource;
            PreparerOperation operation = new PreparerOperation(recordingDataSource.buildPreparer());
            dataSource = (DataSource) AopProxyUtils.getSingletonTarget(dataSource); // TODO: use java.sql.Wrapper.unwrap instead
            apply(operation);
            refreshDatabase(); // TODO: refresh database only when it is necessary
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        stopRecording();
        initialized = true;
//        dataSource = null; // TODO;
        apply(new TagOperation(event));
    }

    @Override
    public synchronized DatabaseSnapshot apply(ContextOperation operation) {
        checkNotNull(operation, "operation must not be null");
//        checkState(!initialized || dataSource == null, "context contains unrecorded changes");
        stopRecording();

        DatabasePrescription description = operation.apply(this);
        DatabaseSnapshot snapshot = new DatabaseSnapshot(operation, description);
        snapshots.add(snapshot);

        if (!initialized) {
            refreshDatabase();
        } else {
            dataSource = null;
        }

        return snapshot;
    }

    private void refreshDatabase() {
        try {
            List<DatabasePreparer> preparers = snapshots.getLast().getDescriptor().getPreparers();
            CompositeDatabasePreparer compositePreparer = new CompositeDatabasePreparer(preparers);

            DatabaseProvider provider = databaseProviders.getProvider(databaseDescriptor);
            DatabaseResult result = provider.createDatabase(compositePreparer);

            if (initialized) { // TODO: skip reporting during initialization phase
                EmbeddedDatabaseReporter.reportDataSource(result.getDataSource());
            }

            dataSource = result.getDataSource();
        } catch (Exception e) {
            throw new RuntimeException(e); // TODO
        }
    }
}
