package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;
import io.zonky.test.db.preparer.RecordingDataSource;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.config.DatabaseProviders;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class DefaultDataSourceContext implements DataSourceContext, ApplicationListener<ContextRefreshedEvent> {

    protected final DatabaseProviders databaseProviders;

    protected DatabaseDescriptor databaseDescriptor;

    protected DataSource dataSource;

    protected List<DatabasePreparer> corePreparers = new LinkedList<>();
    protected List<DatabasePreparer> testPreparers = new LinkedList<>();

    protected boolean initialized = false;
    protected boolean dirty = false; // TODO: improve the detection of non-tracked changes

    public DefaultDataSourceContext(DatabaseProviders databaseProviders) {
        this.databaseProviders = databaseProviders;
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
    public synchronized Object getTarget() {
        if (initialized && dataSource == null) {
            refreshDatabase();
        }

        if (initialized) {
            dirty = true;
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
    public void onApplicationEvent(ContextRefreshedEvent event) {
        stopRecording();
        initialized = true;
    }

    @Override
    public void setDescriptor(DatabaseDescriptor descriptor) {
        stopRecording();
        this.databaseDescriptor = descriptor;
        refreshDatabase();
    }

    @Override
    public void reset() {
        checkState(initialized, "data source context must be initialized");
        stopRecording();

        testPreparers.clear();
        dataSource = null;
        dirty = false;
    }

    @Override
    public void apply(DatabasePreparer preparer) {
        checkNotNull(preparer, "preparer must not be null");
        stopRecording();

        if (!initialized) {
            corePreparers.add(preparer);
            refreshDatabase();
        } else if (!dirty) {
            testPreparers.add(preparer);
            dataSource = null;
        } else {
            try {
                preparer.prepare(dataSource);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private synchronized void stopRecording() {
        if (dataSource instanceof RecordingDataSource) {
            RecordingDataSource recordingDataSource = (RecordingDataSource) this.dataSource;
            Optional<DatabasePreparer> recordedPreparer = recordingDataSource.getPreparer();
            dataSource = (DataSource) AopProxyUtils.getSingletonTarget(dataSource); // TODO: use java.sql.Wrapper.unwrap instead
            dirty = false;

            recordedPreparer.ifPresent(preparer -> {
                corePreparers.add(preparer);
                refreshDatabase(); // TODO: refresh database only when it is necessary
            });
        }
    }

    private void refreshDatabase() {
        dirty = false;

        List<DatabasePreparer> preparers = ImmutableList.<DatabasePreparer>builder()
                .addAll(corePreparers)
                .addAll(testPreparers)
                .build();

        checkState(databaseDescriptor != null, "database descriptor must be set");
        DatabaseProvider provider = databaseProviders.getProvider(databaseDescriptor);

        try {
            dataSource = provider.createDatabase(new CompositeDatabasePreparer(preparers));
        } catch (Exception e) {
            throw new RuntimeException(e); // TODO
        }
    }
}
