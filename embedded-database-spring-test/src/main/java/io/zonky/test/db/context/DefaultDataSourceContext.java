/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.context;

import com.google.common.collect.ImmutableList;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.preparer.RecordingDataSource;
import io.zonky.test.db.preparer.ReplayableDatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.config.DatabaseProviders;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.context.transaction.TestTransaction;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.zonky.test.db.context.DataSourceContext.State.AHEAD;
import static io.zonky.test.db.context.DataSourceContext.State.DIRTY;
import static io.zonky.test.db.context.DataSourceContext.State.FRESH;
import static io.zonky.test.db.context.DataSourceContext.State.INITIALIZING;

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
    public synchronized Object getTarget() {
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
        checkState(getState() != DIRTY, "Data source context must not be dirty");
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
        checkState(getState() != INITIALIZING, "Data source context must be initialized");
        checkState(!TestTransaction.isActive(), "Cannot reset the data source context without ending the existing transaction first");

        if (getState() != FRESH) {
            testPreparers.clear();
            cleanDatabase();
        }
    }

    @Override
    public synchronized void apply(DatabasePreparer preparer) {
        checkNotNull(preparer, "Preparer must not be null");
        stopRecording();

        if (getState() == INITIALIZING) {
            corePreparers.add(preparer);
            refreshDatabase();
        } else if (getState() != DIRTY) {
            testPreparers.add(preparer);
            cleanDatabase();
        } else {
            try {
                preparer.prepare(dataSource);
            } catch (SQLException e) {
                throw new IllegalStateException("Unknown error occurred while applying the preparer", e);
            }
        }
    }

    private synchronized void stopRecording() {
        if (dataSource instanceof RecordingDataSource) {
            RecordingDataSource recordingDataSource = (RecordingDataSource) this.dataSource;
            ReplayableDatabasePreparer recordedPreparer = recordingDataSource.getPreparer();

            if (recordedPreparer.hasRecords()) {
                corePreparers.add(recordedPreparer);
            }

            dataSource = (EmbeddedDatabase) AopProxyUtils.getSingletonTarget(dataSource);
            dirty = false;
        }
    }

    private synchronized void refreshDatabase() {
        cleanDatabase();

        List<DatabasePreparer> preparers = ImmutableList.<DatabasePreparer>builder()
                .addAll(corePreparers)
                .addAll(testPreparers)
                .build();

        checkState(databaseDescriptor != null, "Database descriptor must be set");
        DatabaseProvider provider = databaseProviders.getProvider(databaseDescriptor);
        dataSource = provider.createDatabase(new CompositeDatabasePreparer(preparers));
    }

    private synchronized void cleanDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
        dataSource = null;
        dirty = false;
    }
}
