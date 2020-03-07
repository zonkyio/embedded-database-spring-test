package io.zonky.test.db.flyway;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.zonky.test.db.preparer.RecordingDataSource;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.config.DatabaseProviders;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.context.transaction.TestTransaction;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.zonky.test.db.flyway.DataSourceContext.State.AHEAD;
import static io.zonky.test.db.flyway.DataSourceContext.State.DIRTY;
import static io.zonky.test.db.flyway.DataSourceContext.State.FRESH;
import static io.zonky.test.db.flyway.DataSourceContext.State.INITIALIZING;

public class DefaultDataSourceContext implements DataSourceContext, ApplicationListener<ContextRefreshedEvent> {

    protected final DatabaseProviders databaseProviders;

    protected DatabaseDescriptor databaseDescriptor;
    protected List<DatabasePreparer> corePreparers = new LinkedList<>();
    protected List<DatabasePreparer> testPreparers = new LinkedList<>();

    protected EmbeddedDatabase dataSource;
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
    public synchronized Object getTarget() throws Exception {
        if (dataSource == null) {
            refreshDatabase();
        }

        if (initialized && !dirty) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            boolean excludedCaller = Arrays.stream(stackTrace)
                    .anyMatch(e -> e.getClassName().equals("io.zonky.test.db.logging.EmbeddedDatabaseTestExecutionListener"));
            if (!excludedCaller) {
                dirty = true;
            }
        }

        if (initialized || dataSource instanceof RecordingDataSource) {
            return dataSource;
        }

        dataSource = (EmbeddedDatabase) RecordingDataSource.wrap(dataSource);
        return dataSource;
    }

    @Override
    public void releaseTarget(Object target) {
        // nothing to do
    }

    @Override
    public synchronized void onApplicationEvent(ContextRefreshedEvent event) {
        stopRecording();
        initialized = true;
    }

    @Override
    public synchronized void setDescriptor(DatabaseDescriptor descriptor) {
        checkState(getState() != DIRTY, "data source context must not be dirty");
        stopRecording();

        this.databaseDescriptor = descriptor;
        cleanDatabase();
    }

    @Override
    public State getState() {
        if (!initialized) {
            return INITIALIZING;
        } else if (dirty) {
            return DIRTY;
        } else if (!testPreparers.isEmpty()) {
            return AHEAD;
        } else {
            return FRESH;
        }
    }

    @Override
    public synchronized void reset() {
        checkState(getState() != INITIALIZING, "data source context must be initialized");
        checkState(!TestTransaction.isActive(), "cannot reset the data source context without ending the existing transaction first");

        if (getState() != FRESH) {
            testPreparers.clear();
            cleanDatabase();
        }
    }

    @Override
    public synchronized void apply(DatabasePreparer preparer) {
        checkNotNull(preparer, "preparer must not be null");
        stopRecording();

        try {
            if (getState() == INITIALIZING) {
                corePreparers.add(preparer);
                refreshDatabase();
            } else if (getState() != DIRTY) {
                testPreparers.add(preparer);
                cleanDatabase();
            } else {
                preparer.prepare(dataSource);
            }
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e); // TODO: maybe use a more specific exception?
        }
    }

    private synchronized void stopRecording() {
        if (dataSource instanceof RecordingDataSource) {
            RecordingDataSource recordingDataSource = (RecordingDataSource) this.dataSource;
            Optional<DatabasePreparer> recordedPreparer = recordingDataSource.getPreparer();
            recordedPreparer.ifPresent(preparer -> corePreparers.add(preparer));

            dataSource = (EmbeddedDatabase) AopProxyUtils.getSingletonTarget(dataSource); // TODO: use java.sql.Wrapper.unwrap instead
            dirty = false;
        }
    }

    private synchronized void refreshDatabase() throws Exception {
        cleanDatabase();

        List<DatabasePreparer> preparers = ImmutableList.<DatabasePreparer>builder()
                .addAll(corePreparers)
                .addAll(testPreparers)
                .build();

        checkState(databaseDescriptor != null, "database descriptor must be set");
        DatabaseProvider provider = databaseProviders.getProvider(databaseDescriptor);
        dataSource = provider.createDatabase(new CompositeDatabasePreparer(preparers));
    }

    private synchronized void cleanDatabase() {
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e); // TODO: maybe use a more specific exception?
            }
        }
        dataSource = null;
        dirty = false;
    }
}
