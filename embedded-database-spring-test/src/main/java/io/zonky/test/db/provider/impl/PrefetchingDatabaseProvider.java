/*
 * Copyright 2019 the original author or authors.
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

package io.zonky.test.db.provider.impl;

import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.GenericDatabaseProvider;
import io.zonky.test.db.provider.MissingDatabaseProviderException;
import io.zonky.test.db.provider.MissingProviderDependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.ListenableFutureTask;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.newSetFromMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

public class PrefetchingDatabaseProvider implements GenericDatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(PrefetchingDatabaseProvider.class);

    private static final ThreadPoolTaskExecutor taskExecutor = new PriorityThreadPoolTaskExecutor();
    private static final ConcurrentMap<PipelineKey, DatabasePipeline> pipelines = new ConcurrentHashMap<>();

    private final int pipelineCacheSize;

    static {
        taskExecutor.setThreadNamePrefix("prefetching-");
        taskExecutor.setAllowCoreThreadTimeOut(true);
        taskExecutor.setKeepAliveSeconds(60);
        taskExecutor.setCorePoolSize(1);
        taskExecutor.initialize();
    }

    private final Map<DatabaseDescriptor, DatabaseProvider> databaseProviders;

    public PrefetchingDatabaseProvider(ObjectProvider<List<DatabaseProvider>> databaseProviders, Environment environment) {
        this.databaseProviders = Optional.ofNullable(databaseProviders.getIfAvailable()).orElse(emptyList()).stream()
                .collect(Collectors.toMap(p -> new DatabaseDescriptor(p.getDatabaseType(), p.getProviderType()), identity()));

        String threadNamePrefix = environment.getProperty("zonky.test.database.prefetching.thread-name-prefix", "prefetching-");
        int concurrency = environment.getProperty("zonky.test.database.prefetching.concurrency", int.class, 3);
        pipelineCacheSize = environment.getProperty("zonky.test.database.prefetching.pipeline-cache-size", int.class, 3);

        taskExecutor.setThreadNamePrefix(threadNamePrefix);
        taskExecutor.setCorePoolSize(concurrency);
    }

    @Override
    public DataSource getDatabase(DatabasePreparer preparer, DatabaseDescriptor descriptor) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        logger.trace("Prefetching pipelines: {}", pipelines.values());

        DatabaseProvider provider = databaseProviders.get(descriptor);
        if (provider == null) {
            throw missingDatabaseProviderException(descriptor);
        }

        PipelineKey key = new PipelineKey(preparer, descriptor, provider);
        DatabasePipeline pipeline = pipelines.computeIfAbsent(key, k -> new DatabasePipeline());

        PreparedResult result = pipeline.results.poll();
        prepareDatabase(key, result == null ? HIGHEST_PRECEDENCE : LOWEST_PRECEDENCE);

        long invocationCount = pipeline.requests.incrementAndGet();
        if (invocationCount == 1) {
            for (int i = 1; i <= pipelineCacheSize; i++) {
                int priority = -1 * (int) (invocationCount / pipelineCacheSize * i);
                prepareDatabase(key, priority);
            }
        } else {
            synchronized (pipeline.tasks) {
                List<PrefetchingTask> cancelledTasks = pipeline.tasks.stream()
                        .filter(t -> t.priority > HIGHEST_PRECEDENCE)
                        .filter(t -> t.cancel(false))
                        .collect(toList());

                for (int i = 1; i <= cancelledTasks.size(); i++) {
                    int priority = -1 * (int) (invocationCount / cancelledTasks.size() * i);
                    prepareDatabase(key, priority);
                }
            }
        }

        DataSource dataSource = result != null ? result.get() : pipeline.results.take().get();
        logger.debug("Database has been successfully returned in {}", stopwatch);
        return dataSource;
    }

    protected MissingDatabaseProviderException missingDatabaseProviderException(DatabaseDescriptor descriptor) {
        boolean isProviderPresent = databaseProviders.keySet().stream()
                .map(DatabaseDescriptor::getProviderType)
                .anyMatch(p -> p.equals(descriptor.getProviderType()));
        if (isProviderPresent) {
            return new MissingDatabaseProviderException(descriptor);
        } else {
            return new MissingProviderDependencyException(descriptor);
        }
    }

    private ListenableFutureTask<DataSource> prepareDatabase(PipelineKey key, int priority) {
        PrefetchingTask task = new PrefetchingTask(key.provider, key.preparer, priority);
        DatabasePipeline pipeline = pipelines.get(key);

        task.addCallback(new ListenableFutureCallback<DataSource>() {
            @Override
            public void onSuccess(DataSource result) {
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

    private static class PipelineKey {

        private final DatabasePreparer preparer;
        private final DatabaseDescriptor descriptor;
        private final DatabaseProvider provider;

        private PipelineKey(DatabasePreparer preparer, DatabaseDescriptor descriptor, DatabaseProvider provider) {
            this.preparer = preparer;
            this.descriptor = descriptor;
            this.provider = provider;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PipelineKey that = (PipelineKey) o;
            return Objects.equals(preparer, that.preparer) &&
                    Objects.equals(descriptor, that.descriptor) &&
                    Objects.equals(provider, that.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hash(preparer, descriptor, provider);
        }
    }

    private static class DatabasePipeline {

        private final AtomicLong requests = new AtomicLong();
        private final Set<PrefetchingTask> tasks = newSetFromMap(new ConcurrentHashMap<>());
        private final BlockingQueue<PreparedResult> results = new LinkedBlockingQueue<>();

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("totalRequests", requests.get())
                    .add("prefetchingQueue", tasks.size())
                    .add("preparedResults", results.size())
                    .toString();
        }
    }

    private static class PreparedResult {

        private final DataSource result;
        private final Throwable error;

        public static PreparedResult success(DataSource result) {
            return new PreparedResult(result, null);
        }

        public static PreparedResult failure(Throwable error) {
            return new PreparedResult(null, error);
        }

        private PreparedResult(DataSource result, Throwable error) {
            this.result = result;
            this.error = error;
        }

        public DataSource get() throws Exception {
            if (result != null) {
                return result;
            }
            Throwables.propagateIfPossible(error, Exception.class);
            throw new RuntimeException(error);
        }
    }

    private static class PriorityThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {

        @Override
        protected BlockingQueue<Runnable> createQueue(int queueCapacity) {
            return new PriorityBlockingQueue<>();
        }
    }

    private static class PrefetchingTask extends ListenableFutureTask<DataSource> implements Comparable<PrefetchingTask> {

        private final AtomicBoolean active = new AtomicBoolean(true);
        private final int priority;

        public PrefetchingTask(DatabaseProvider provider, DatabasePreparer preparer, int priority) {
            super(() -> provider.getDatabase(preparer));
            this.priority = priority;
        }

        @Override
        public void run() {
            if (active.compareAndSet(true, false)) {
                super.run();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (mayInterruptIfRunning || active.compareAndSet(true, false)) {
                return super.cancel(mayInterruptIfRunning);
            } else {
                return false;
            }
        }

        @Override
        public int compareTo(PrefetchingTask task) {
            return Integer.compare(priority, task.priority);
        }
    }
}
