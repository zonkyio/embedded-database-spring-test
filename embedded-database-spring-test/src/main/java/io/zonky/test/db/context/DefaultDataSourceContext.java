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
import io.zonky.test.db.event.TestExecutionFinishedEvent;
import io.zonky.test.db.event.TestExecutionStartedEvent;
import io.zonky.test.db.logging.EmbeddedDatabaseReporter;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.preparer.RecordingDataSource;
import io.zonky.test.db.preparer.ReplayableDatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.transaction.TestTransaction;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.zonky.test.db.context.DefaultDataSourceContext.DatabaseState.DIRTY;
import static io.zonky.test.db.context.DefaultDataSourceContext.DatabaseState.FRESH;
import static io.zonky.test.db.context.DefaultDataSourceContext.DatabaseState.RESET;
import static io.zonky.test.db.context.DefaultDataSourceContext.ExecutionPhase.INITIALIZING;
import static io.zonky.test.db.context.DefaultDataSourceContext.ExecutionPhase.TEST_EXECUTION;
import static io.zonky.test.db.context.DefaultDataSourceContext.ExecutionPhase.TEST_PREPARATION;

public class DefaultDataSourceContext implements DataSourceContext, BeanNameAware, DisposableBean {

    protected final DatabaseProvider databaseProvider;

    protected final List<DatabasePreparer> corePreparers = new LinkedList<>();
    protected final List<DatabasePreparer> testPreparers = new LinkedList<>();

    protected String beanName;
    protected Thread mainThread;

    protected ExecutionPhase executionPhase = INITIALIZING;
    protected DatabaseState databaseState = RESET;
    protected EmbeddedDatabase database;

    public DefaultDataSourceContext(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public synchronized List<DatabasePreparer> getCorePreparers() {
        return ImmutableList.copyOf(corePreparers);
    }

    @Override
    public synchronized List<DatabasePreparer> getTestPreparers() {
        return ImmutableList.copyOf(testPreparers);
    }

    @Override
    public synchronized EmbeddedDatabase getDatabase() {
        if (databaseState == RESET && !isRefreshAllowed()) {
            return database;
        }

        if (databaseState == RESET) {
            refreshDatabase();
        }

        if (executionPhase != INITIALIZING && databaseState != DIRTY) {
            databaseState = DIRTY;
        }

        if (executionPhase != INITIALIZING || database instanceof RecordingDataSource) {
            return database;
        }

        database = (EmbeddedDatabase) RecordingDataSource.wrap(database);
        return database;
    }

    private boolean isRefreshAllowed() {
        return database == null
                || executionPhase == INITIALIZING
                || executionPhase == TEST_EXECUTION
                || Thread.currentThread() == mainThread;
    }

    @Override
    public ContextState getState() {
        if (executionPhase == INITIALIZING) {
            return ContextState.INITIALIZING;
        } else if (databaseState == DIRTY) {
            return ContextState.DIRTY;
        } else if (!testPreparers.isEmpty()) {
            return ContextState.AHEAD;
        } else {
            return ContextState.FRESH;
        }
    }

    @EventListener
    public synchronized void handleContextRefreshed(ContextRefreshedEvent event) {
        stopRecording();
        mainThread = Thread.currentThread();
        executionPhase = TEST_PREPARATION;
    }

    @EventListener
    public synchronized void handleTestStarted(TestExecutionStartedEvent event) {
        executionPhase = TEST_EXECUTION;

        if (databaseState == RESET) {
            refreshDatabase();
        }

        String databaseBeanName = StringUtils.substringBeforeLast(beanName, "Context");
        EmbeddedDatabaseReporter.reportDataSource(databaseBeanName, database, event.getTestMethod());
    }

    @EventListener
    public synchronized void handleTestFinished(TestExecutionFinishedEvent event) {
        executionPhase = TEST_PREPARATION;
    }

    @Override
    public synchronized void reset() {
        checkState(getState() != ContextState.INITIALIZING, "Data source context must be initialized");
        checkState(!TestTransaction.isActive(), "Cannot reset the data source context without ending the existing transaction first");

        if (getState() != ContextState.FRESH) {
            testPreparers.clear();
            resetDatabase();
        }
    }

    @Override
    public synchronized void apply(DatabasePreparer preparer) {
        checkNotNull(preparer, "Preparer must not be null");
        stopRecording();

        if (getState() == ContextState.INITIALIZING) {
            corePreparers.add(preparer);
            refreshDatabase();
        } else if (getState() != ContextState.DIRTY) {
            testPreparers.add(preparer);
            resetDatabase();
        } else {
            try {
                preparer.prepare(database);
            } catch (SQLException e) {
                throw new IllegalStateException("Unknown error when applying the preparer", e);
            }
        }
    }

    @Override
    public synchronized void destroy() {
        if (database != null) {
            database.close();
        }
    }

    private synchronized void stopRecording() {
        if (database instanceof RecordingDataSource) {
            RecordingDataSource recordingDataSource = (RecordingDataSource) this.database;
            ReplayableDatabasePreparer recordedPreparer = recordingDataSource.getPreparer();

            if (recordedPreparer.hasRecords()) {
                corePreparers.add(recordedPreparer);
            }

            database = (EmbeddedDatabase) AopProxyUtils.getSingletonTarget(database);
            databaseState = FRESH;
        }
    }

    private synchronized void refreshDatabase() {
        if (database != null) {
            database.close();
        }

        List<DatabasePreparer> preparers = ImmutableList.<DatabasePreparer>builder()
                .addAll(corePreparers)
                .addAll(testPreparers)
                .build();

        database = databaseProvider.createDatabase(new CompositeDatabasePreparer(preparers));
        databaseState = FRESH;
    }

    private synchronized void resetDatabase() {
        databaseState = RESET;
    }

    protected enum ExecutionPhase {

        INITIALIZING,
        TEST_PREPARATION,
        TEST_EXECUTION

    }

    protected enum DatabaseState {

        FRESH,
        DIRTY, // TODO: improve the detection of non-tracked changes
        RESET

    }
}
