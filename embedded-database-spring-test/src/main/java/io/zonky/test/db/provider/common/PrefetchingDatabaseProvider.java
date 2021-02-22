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

package io.zonky.test.db.provider.common;

import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.ListenableFutureTask;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.zonky.test.db.provider.common.PrefetchingDatabaseProvider.DatabasePipeline.State.INITIALIZED;
import static io.zonky.test.db.provider.common.PrefetchingDatabaseProvider.DatabasePipeline.State.INITIALIZING;
import static io.zonky.test.db.provider.common.PrefetchingDatabaseProvider.DatabasePipeline.State.NEW;
import static io.zonky.test.db.provider.common.PrefetchingDatabaseProvider.PrefetchingTask.TaskType.EXISTING_DATABASE;
import static io.zonky.test.db.provider.common.PrefetchingDatabaseProvider.PrefetchingTask.TaskType.NEW_DATABASE;
import static java.util.Collections.newSetFromMap;
import static java.util.stream.Collectors.toList;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

public class PrefetchingDatabaseProvider implements DatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(PrefetchingDatabaseProvider.class);

    protected static final ThreadPoolTaskExecutor taskExecutor = new PriorityThreadPoolTaskExecutor();
    protected static final ConcurrentMap<PipelineKey, DatabasePipeline> pipelines = new ConcurrentHashMap<>();
    protected static final AtomicLong databaseCount = new AtomicLong();

    protected final int pipelineMaxCacheSize;

    static {
        taskExecutor.setThreadNamePrefix("prefetching-");
        taskExecutor.setAllowCoreThreadTimeOut(true);
        taskExecutor.setKeepAliveSeconds(60);
        taskExecutor.setCorePoolSize(1);
        taskExecutor.initialize();
    }

    protected final DatabaseProvider provider;

    public PrefetchingDatabaseProvider(DatabaseProvider provider, Environment environment) {
        this.provider = provider;

        String threadNamePrefix = environment.getProperty("zonky.test.database.prefetching.thread-name-prefix", "prefetching-");
        int concurrency = environment.getProperty("zonky.test.database.prefetching.concurrency", int.class, 3);
        pipelineMaxCacheSize = environment.getProperty("zonky.test.database.prefetching.pipeline-cache-size", int.class, 3);

        taskExecutor.setThreadNamePrefix(threadNamePrefix);
        taskExecutor.setCorePoolSize(concurrency);
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        logger.trace("Prefetching pipelines: {}", pipelines.values());
        databaseCount.decrementAndGet();

        PipelineKey key = new PipelineKey(provider, preparer);
        DatabasePipeline pipeline = pipelines.computeIfAbsent(key, k -> new DatabasePipeline());
        PreparedResult result = pipeline.results.poll();

        if (result != null) {
            prepareDatabase(key, LOWEST_PRECEDENCE);
        } else {
            boolean pipelineInitMode = pipeline.state.compareAndSet(NEW, INITIALIZING);
            Optional<PrefetchingTask> task = prepareExistingDatabase(key, HIGHEST_PRECEDENCE);
            if (pipelineInitMode || !task.isPresent()) {
                prepareNewDatabase(key, HIGHEST_PRECEDENCE);
            }
        }

        long invocationCount = pipeline.requests.incrementAndGet();
        long databasesCount = pipeline.tasks.size() + pipeline.results.size();
        if (result == null) databasesCount--;

        if (databasesCount < invocationCount - 1 && databasesCount < pipelineMaxCacheSize) {
            prepareDatabase(key, -1);
        }
        reschedulePipeline(key);

        if (result == null) {
            try {
                result = pipeline.results.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProviderException("Provider interrupted", e);
            }
        }

        EmbeddedDatabase database = result.get();
        logger.debug("Database has been successfully fetched in {} - pipelineKey={}", stopwatch, pipeline.key);
        return database;
    }

    protected PrefetchingTask prepareDatabase(PipelineKey key, int priority) {
        DatabasePipeline pipeline = pipelines.get(key);

        if (pipeline.state.get() != INITIALIZED) {
            return prepareExistingDatabase(key, priority)
                    .orElseGet(() -> prepareNewDatabase(key, priority));
        }

        return prepareNewDatabase(key, priority);
    }

    protected PrefetchingTask prepareNewDatabase(PipelineKey key, int priority) {
        databaseCount.incrementAndGet();

        Pair<PipelineKey, EmbeddedDatabase> databaseToRemove = findDatabaseToRemove().orElse(null);
        if (databaseToRemove != null) {
            databaseCount.decrementAndGet();

            if (databaseToRemove.getKey().equals(key)) {
                return executeTask(key, PrefetchingTask.withDatabase(databaseToRemove.getValue(), priority));
            } else {
                databaseToRemove.getValue().close();
                DatabasePipeline pipeline = pipelines.get(databaseToRemove.getKey());
                logger.trace("Prepared database has been cleaned: {}", pipeline.key);
            }
        }

        return executeTask(key, PrefetchingTask.forPreparer(key.provider, key.preparer, priority));
    }

    protected Optional<PrefetchingTask> prepareExistingDatabase(PipelineKey key, int priority) {
        CompositeDatabasePreparer compositePreparer = key.preparer instanceof CompositeDatabasePreparer ?
                (CompositeDatabasePreparer) key.preparer : new CompositeDatabasePreparer(ImmutableList.of(key.preparer));
        List<DatabasePreparer> preparers = compositePreparer.getPreparers();

        for (int i = preparers.size() - 1; i > 0; i--) {
            CompositeDatabasePreparer pipelinePreparer = new CompositeDatabasePreparer(preparers.subList(0, i));
            PipelineKey pipelineKey = new PipelineKey(provider, pipelinePreparer);
            DatabasePipeline existingPipeline = pipelines.get(pipelineKey);

            if (existingPipeline != null) {
                if (key.preparer.estimatedDuration() - pipelinePreparer.estimatedDuration() > 600) {
                    return Optional.empty();
                }

                PreparedResult result = existingPipeline.results.poll();
                if (result != null) {
                    CompositeDatabasePreparer complementaryPreparer = new CompositeDatabasePreparer(preparers.subList(i, preparers.size()));
                    logger.trace("Preparing existing database from {} pipeline by using the complementary preparer {}", existingPipeline.key, complementaryPreparer);
                    PrefetchingTask task = executeTask(key, PrefetchingTask.withDatabase(result.get(), complementaryPreparer, priority));

                    prepareDatabase(pipelineKey, LOWEST_PRECEDENCE);
                    reschedulePipeline(pipelineKey);

                    return Optional.of(task);
                }
            }
        }

        return Optional.empty();
    }

    protected void reschedulePipeline(PipelineKey key) {
        DatabasePipeline pipeline = pipelines.get(key);

        synchronized (pipeline.tasks) {
            long invocationCount = pipeline.requests.get();

            List<PrefetchingTask> cancelledTasks = pipeline.tasks.stream()
                    .filter(t -> t.priority > HIGHEST_PRECEDENCE)
                    .filter(t -> t.cancel(false))
                    .collect(toList());

            for (int i = 0; i < cancelledTasks.size(); i++) {
                int priority = -1 * (int) (invocationCount / cancelledTasks.size() * (i + 1));
                executeTask(key, PrefetchingTask.fromTask(cancelledTasks.get(i), priority));
            }
        }
    }

    protected PrefetchingTask executeTask(PipelineKey key, PrefetchingTask task) {
        DatabasePipeline pipeline = pipelines.get(key);

        task.addCallback(new ListenableFutureCallback<EmbeddedDatabase>() {
            @Override
            public void onSuccess(EmbeddedDatabase result) {
                if (task.type == NEW_DATABASE) {
                    pipeline.state.set(INITIALIZED);
                }
                pipeline.tasks.remove(task);
                pipeline.results.offer(PreparedResult.success(result));
            }

            @Override
            public void onFailure(Throwable error) {
                pipeline.tasks.remove(task);
                if (!(error instanceof CancellationException)) {
                    pipeline.results.offer(PreparedResult.failure(error));
                }
            }
        });

        pipeline.tasks.add(task);
        taskExecutor.execute(task);
        return task;
    }

    protected Optional<Pair<PipelineKey, EmbeddedDatabase>> findDatabaseToRemove() {
        while (databaseCount.get() > 35) {
            long timestampThreshold = System.currentTimeMillis() - 10_000;

            PipelineKey key = pipelines.entrySet().stream()
                    .map(e -> Pair.of(e.getKey(), e.getValue().results.peek()))
                    .filter(e -> e.getValue() != null && e.getValue().getTimestamp() < timestampThreshold)
                    .min(Comparator.comparing(e -> e.getValue().getTimestamp()))
                    .map(Pair::getKey).orElse(null);

            if (key == null) {
                return Optional.empty();
            }

            DatabasePipeline pipeline = pipelines.get(key);
            if (pipeline != null) {
                PreparedResult result = pipeline.results.poll();
                if (result != null) {
                    if (result.hasResult()) {
                        return Optional.of(Pair.of(key, result.get()));
                    } else {
                        databaseCount.decrementAndGet();
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrefetchingDatabaseProvider that = (PrefetchingDatabaseProvider) o;
        return pipelineMaxCacheSize == that.pipelineMaxCacheSize &&
                Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pipelineMaxCacheSize, provider);
    }

    protected static class PipelineKey {

        public final DatabaseProvider provider;
        public final DatabasePreparer preparer;

        protected PipelineKey(DatabaseProvider provider, DatabasePreparer preparer) {
            this.provider = provider;
            this.preparer = preparer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PipelineKey that = (PipelineKey) o;
            return Objects.equals(provider, that.provider) &&
                    Objects.equals(preparer, that.preparer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, preparer);
        }
    }

    protected static class DatabasePipeline {

        public final String key = RandomStringUtils.randomAlphabetic(8);
        public final AtomicReference<State> state = new AtomicReference<>(NEW);
        public final AtomicLong requests = new AtomicLong();
        public final Set<PrefetchingTask> tasks = newSetFromMap(new ConcurrentHashMap<>());
        public final BlockingQueue<PreparedResult> results = new LinkedBlockingQueue<>();

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("pipelineKey", key)
                    .add("pipelineState", state.get())
                    .add("totalRequests", requests.get())
                    .add("prefetchingQueue", tasks.size())
                    .add("preparedResults", results.size())
                    .toString();
        }

        protected enum State {

            NEW, INITIALIZING, INITIALIZED

        }
    }

    protected static class PreparedResult {

        private final long timestamp = System.currentTimeMillis();
        private final EmbeddedDatabase result;
        private final Throwable error;

        public static PreparedResult success(EmbeddedDatabase result) {
            return new PreparedResult(result, null);
        }

        public static PreparedResult failure(Throwable error) {
            return new PreparedResult(null, error);
        }

        protected PreparedResult(EmbeddedDatabase result, Throwable error) {
            this.result = result;
            this.error = error;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean hasResult() {
            return result != null;
        }

        public EmbeddedDatabase get() throws ProviderException {
            if (result != null) {
                return result;
            }
            Throwables.throwIfInstanceOf(error, ProviderException.class);
            throw new ProviderException("Unexpected error when prefetching a database", error);
        }
    }

    protected static class PriorityThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {

        @Override
        protected BlockingQueue<Runnable> createQueue(int queueCapacity) {
            return new PriorityBlockingQueue<>();
        }
    }

    protected static class PrefetchingTask extends ListenableFutureTask<EmbeddedDatabase> implements Comparable<PrefetchingTask> {

        private final AtomicBoolean executed = new AtomicBoolean(false);

        public final Callable<EmbeddedDatabase> action;
        public final TaskType type;
        public final int priority;

        public static PrefetchingTask forPreparer(DatabaseProvider provider, DatabasePreparer preparer, int priority) {
            return new PrefetchingTask(priority, NEW_DATABASE, () -> provider.createDatabase(preparer));
        }

        public static PrefetchingTask withDatabase(EmbeddedDatabase database, DatabasePreparer preparer, int priority) {
            return new PrefetchingTask(priority, EXISTING_DATABASE, () -> {
                preparer.prepare(database);
                return database;
            });
        }

        public static PrefetchingTask withDatabase(EmbeddedDatabase database, int priority) {
            return new PrefetchingTask(priority, EXISTING_DATABASE, () -> database);
        }

        public static PrefetchingTask fromTask(PrefetchingTask task, int priority) {
            return new PrefetchingTask(priority, task.type, task.action);
        }

        private PrefetchingTask(int priority, TaskType type, Callable<EmbeddedDatabase> action) {
            super(action);

            this.action = action;
            this.type = type;
            this.priority = priority;
        }

        @Override
        public void run() {
            if (executed.compareAndSet(false, true)) {
                super.run();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (mayInterruptIfRunning || executed.compareAndSet(false, true)) {
                return super.cancel(mayInterruptIfRunning);
            } else {
                return false;
            }
        }

        @Override
        public int compareTo(PrefetchingTask task) {
            return Integer.compare(priority, task.priority);
        }

        protected enum TaskType {

            NEW_DATABASE, EXISTING_DATABASE

        }
    }
}
