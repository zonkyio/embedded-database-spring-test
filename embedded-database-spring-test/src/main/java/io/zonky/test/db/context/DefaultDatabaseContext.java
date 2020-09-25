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
import com.google.common.util.concurrent.Futures;
import io.zonky.test.db.event.TestExecutionFinishedEvent;
import io.zonky.test.db.event.TestExecutionStartedEvent;
import io.zonky.test.db.logging.EmbeddedDatabaseReporter;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.preparer.RecordingDataSource;
import io.zonky.test.db.preparer.ReplayableDatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.support.DatabaseDefinition;
import io.zonky.test.db.support.DatabaseProviders;
import io.zonky.test.db.support.ProviderDescriptor;
import io.zonky.test.db.support.ProviderResolver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.zonky.test.db.context.DefaultDatabaseContext.DatabaseState.DIRTY;
import static io.zonky.test.db.context.DefaultDatabaseContext.DatabaseState.FRESH;
import static io.zonky.test.db.context.DefaultDatabaseContext.DatabaseState.RECORDING;
import static io.zonky.test.db.context.DefaultDatabaseContext.DatabaseState.RESET;
import static io.zonky.test.db.context.DefaultDatabaseContext.ExecutionPhase.INITIALIZING;
import static io.zonky.test.db.context.DefaultDatabaseContext.ExecutionPhase.TEST_EXECUTION;
import static io.zonky.test.db.context.DefaultDatabaseContext.ExecutionPhase.TEST_PREPARATION;
import static org.springframework.aop.interceptor.AsyncExecutionAspectSupport.DEFAULT_TASK_EXECUTOR_BEAN_NAME;

public class DefaultDatabaseContext implements DatabaseContext, BeanNameAware, BeanFactoryAware, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDatabaseContext.class);

    protected final DatabaseDefinition databaseDefinition;

    protected DatabaseProvider databaseProvider;
    protected AsyncTaskExecutor bootstrapExecutor;

    protected String beanName;
    protected Thread mainThread;

    protected List<DatabasePreparer> corePreparers = new LinkedList<>();
    protected List<DatabasePreparer> testPreparers = new LinkedList<>();

    protected ExecutionPhase executionPhase = INITIALIZING;
    protected DatabaseState databaseState = RESET;

    protected Future<EmbeddedDatabase> database;

    public DefaultDatabaseContext(DatabaseDefinition databaseDefinition) {
        this.databaseDefinition = databaseDefinition;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        ProviderResolver providerResolver = beanFactory.getBean(ProviderResolver.class);
        DatabaseProviders databaseProviders = beanFactory.getBean(DatabaseProviders.class);
        ProviderDescriptor providerDescriptor = providerResolver.getDescriptor(databaseDefinition);

        this.databaseProvider = databaseProviders.getProvider(providerDescriptor);
        this.bootstrapExecutor = determineBootstrapExecutor(beanFactory);
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
            return awaitDatabase();
        }

        if (databaseState == RESET) {
            refreshDatabase();
        }

        if (executionPhase != INITIALIZING && databaseState != DIRTY) {
            databaseState = DIRTY;
        }

        if (executionPhase != INITIALIZING || databaseState == RECORDING) {
            return awaitDatabase();
        }

        logger.trace("Starting database recording - context={}", beanName);
        database = databaseFuture(RecordingDataSource.wrap(awaitDatabase()));
        databaseState = RECORDING;

        return awaitDatabase();
    }

    private boolean isRefreshAllowed() {
        // TODO: only temporary logging
        if (mainThread != null && Thread.currentThread().getName().equals("main") && Thread.currentThread() != mainThread) {
            logger.warn("Threads are different - initThread={}@{}, currentThread={}@{}",
                    mainThread, mainThread.hashCode(), Thread.currentThread(), Thread.currentThread().hashCode());
        }
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
        if (event.getApplicationContext().containsBean(beanName) && mainThread == null) {
            stopRecording();
            mainThread = Thread.currentThread();
            executionPhase = TEST_PREPARATION;
            logger.trace("Execution phase has been changed to {} - context={}", executionPhase, beanName);
        }
    }

    @EventListener
    public synchronized void handleTestStarted(TestExecutionStartedEvent event) {
        executionPhase = TEST_EXECUTION;

        if (databaseState == RESET) {
            refreshDatabase();
        }

        String databaseBeanName = StringUtils.substringBeforeLast(beanName, "Context");
        EmbeddedDatabaseReporter.reportDataSource(databaseBeanName, awaitDatabase(), event.getTestMethod());

        logger.trace("Execution phase has been changed to {} - context={}", executionPhase, beanName);
    }

    @EventListener
    public synchronized void handleTestFinished(TestExecutionFinishedEvent event) {
        executionPhase = TEST_PREPARATION;
        logger.trace("Execution phase has been changed to {} - context={}", executionPhase, beanName);
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
                preparer.prepare(awaitDatabase());
            } catch (SQLException e) {
                throw new IllegalStateException("Unknown error when applying the preparer", e);
            }
        }
    }

    @Override
    public synchronized void destroy() {
        logger.trace("Closing database context bean - context={}", beanName);
        if (database != null) {
            awaitDatabase().close();
        }
    }

    private synchronized void stopRecording() {
        if (databaseState == RECORDING) {
            logger.trace("Stopping database recording - context={}", beanName);

            RecordingDataSource recordingDataSource = (RecordingDataSource) awaitDatabase();
            ReplayableDatabasePreparer recordedPreparer = recordingDataSource.getPreparer();

            if (recordedPreparer.hasRecords()) {
                corePreparers.add(recordedPreparer);
            }

            database = databaseFuture(AopProxyUtils.getSingletonTarget(awaitDatabase()));
            databaseState = FRESH;
        }
    }

    private synchronized void refreshDatabase() {
        if (database != null) {
            logger.trace("Closing previous database - context={}", beanName);
            awaitDatabase().close();
        }

        logger.trace("Refreshing database context - context={}, corePreparers={}, testPreparers={}", beanName, corePreparers, testPreparers);

        List<DatabasePreparer> preparers = ImmutableList.<DatabasePreparer>builder()
                .addAll(corePreparers)
                .addAll(testPreparers)
                .build();

        if (executionPhase == INITIALIZING) {
            database = bootstrapExecutor.submit(() -> databaseProvider.createDatabase(new CompositeDatabasePreparer(preparers)));
        } else {
            database = databaseFuture(databaseProvider.createDatabase(new CompositeDatabasePreparer(preparers)));
        }

        databaseState = FRESH;
    }

    private synchronized void resetDatabase() {
        databaseState = RESET;
    }

    private EmbeddedDatabase awaitDatabase() {
        return Futures.getUnchecked(database);
    }

    private Future<EmbeddedDatabase> databaseFuture(Object database) {
        SettableListenableFuture<EmbeddedDatabase> future = new SettableListenableFuture<>();
        future.set((EmbeddedDatabase) database);
        return future;
    }

    private AsyncTaskExecutor determineBootstrapExecutor(BeanFactory beanFactory) {
        Executor executor;

        try {
            executor = beanFactory.getBean(TaskExecutor.class);
        } catch (NoSuchBeanDefinitionException ex1) {
            try {
                executor = beanFactory.getBean("applicationTaskExecutor", Executor.class);
            } catch (NoSuchBeanDefinitionException ex2) {
                try {
                    executor = beanFactory.getBean(DEFAULT_TASK_EXECUTOR_BEAN_NAME, Executor.class);
                } catch (NoSuchBeanDefinitionException ex3) {
                    executor = new SimpleAsyncTaskExecutor();
                }
            }
        }

        return (executor instanceof AsyncTaskExecutor ?
                (AsyncTaskExecutor) executor : new TaskExecutorAdapter(executor));
    }

    protected enum ExecutionPhase {

        INITIALIZING,
        TEST_PREPARATION,
        TEST_EXECUTION

    }

    protected enum DatabaseState {

        FRESH,
        DIRTY, // TODO: improve the detection of non-tracked changes
        RECORDING,
        RESET

    }
}
