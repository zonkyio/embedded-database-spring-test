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

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.DatabaseRequest;
import io.zonky.test.db.provider.DatabaseTemplate;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class TemplatingDatabaseProvider implements DatabaseProvider {

    public static final CompositeDatabasePreparer EMPTY_PREPARER = new CompositeDatabasePreparer(Collections.emptyList());

    private static final ConcurrentMap<TemplateKey, TemplateWrapper> templates = new ConcurrentHashMap<>();
    private static final ConcurrentMap<TemplateKey, PreparerStats> stats = new ConcurrentHashMap<>();

    private final TemplatableDatabaseProvider provider;
    private final Config config;

    public TemplatingDatabaseProvider(TemplatableDatabaseProvider provider) {
        this(provider, Config.builder().build());
    }

    public TemplatingDatabaseProvider(TemplatableDatabaseProvider provider, Config config) {
        this.provider = provider;
        this.config = config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplatingDatabaseProvider that = (TemplatingDatabaseProvider) o;
        return Objects.equals(provider, that.provider) &&
                Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, config);
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
        CompositeDatabasePreparer compositePreparer = preparer instanceof CompositeDatabasePreparer ?
                (CompositeDatabasePreparer) preparer : new CompositeDatabasePreparer(ImmutableList.of(preparer));
        List<DatabasePreparer> preparers = compositePreparer.getPreparers();

        PreparerStats preparerStats = stats.computeIfAbsent(new TemplateKey(provider, compositePreparer), key -> new PreparerStats());
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            for (int i = preparers.size(); i > 0; i--) {
                CompositeDatabasePreparer templatePreparer = new CompositeDatabasePreparer(preparers.subList(0, i));
                TemplateWrapper existingTemplate = templates.get(new TemplateKey(provider, templatePreparer));

                if (existingTemplate != null) {
                    CompositeDatabasePreparer complementaryPreparer = new CompositeDatabasePreparer(preparers.subList(i, preparers.size()));
                    if (i == preparers.size()) {
                        return createDatabase(complementaryPreparer, existingTemplate, false);
                    } else {
                        return createDatabase(complementaryPreparer, existingTemplate, true);
                    }
                }
            }

            return createDatabase(compositePreparer, null, true);
        } finally {
            preparerStats.onLoad(stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private EmbeddedDatabase createDatabase(CompositeDatabasePreparer preparer, TemplateWrapper template, boolean createNewTemplate) {
        if (createNewTemplate) {
            TemplateWrapper newTemplate = createTemplateIfPossible(preparer, template);
            if (newTemplate != null) {
                return newTemplate.createDatabase(EMPTY_PREPARER);
            }
        }

        if (template != null) {
            return template.createDatabase(preparer);
        } else {
            return provider.createDatabase(DatabaseRequest.of(mergedPreparer(preparer, template)));
        }
    }

    private DatabaseTemplate createTemplate(CompositeDatabasePreparer preparer, TemplateWrapper template) {
        if (template != null) {
            return template.createTemplate(preparer);
        } else {
            return provider.createTemplate(DatabaseRequest.of(preparer));
        }
    }

    private TemplateWrapper createTemplateIfPossible(CompositeDatabasePreparer preparer, TemplateWrapper template) {
        CompositeDatabasePreparer templatePreparer = mergedPreparer(preparer, template);
        TemplateKey templateKey = new TemplateKey(provider, templatePreparer);

        PreparerStats preparerStats = stats.get(templateKey);
        if (preparerStats.getTotalLoadTime() < config.getDurationThreshold()) {
            return null;
        }

        TemplateWrapper oldTemplate = null;
        TemplateWrapper newTemplate;

        synchronized (templates) {
            TemplateWrapper existingTemplate = templates.get(templateKey);
            if (existingTemplate != null) {
                return existingTemplate;
            }

            if (templateCount() >= config.getMaxTemplateCount()) {
                TemplateKey templateToRemove = findTemplateToRemove();
                if (templateToRemove == null) {
                    return null;
                }
                PreparerStats templateToRemoveStats = stats.get(templateToRemove);
                if (preparerStats.getTotalLoadTime() < templateToRemoveStats.getTotalLoadTime() + config.getDurationThreshold()) {
                    return null;
                }
                oldTemplate = templates.remove(templateToRemove);
            }

            newTemplate = new TemplateWrapper(provider, templatePreparer);
            templates.put(templateKey, newTemplate);
        }

        if (oldTemplate != null) {
            oldTemplate.close();
        }

        newTemplate.loadTemplate(() -> createTemplate(preparer, template));
        return newTemplate;
    }

    private long templateCount() {
        return templates.keySet().stream()
                .filter(key -> key.provider.equals(provider))
                .count();
    }

    private TemplateKey findTemplateToRemove() {
        return templates.entrySet().stream()
                .filter(entry -> entry.getValue().isLoaded())
                .map(Map.Entry::getKey)
                .filter(key -> key.provider.equals(provider))
                .min(Comparator.comparing(key -> stats.get(key).getTotalLoadTime()))
                .orElse(null);
    }

    private static CompositeDatabasePreparer mergedPreparer(CompositeDatabasePreparer preparer, TemplateWrapper template) {
        if (template == null) {
            return preparer;
        }
        CompositeDatabasePreparer templatePreparer = template.getPreparer();
        Iterable<DatabasePreparer> combinedPreparers = Iterables.concat(templatePreparer.getPreparers(), preparer.getPreparers());
        return new CompositeDatabasePreparer(ImmutableList.copyOf(combinedPreparers));
    }

    protected static class TemplateKey {

        private final TemplatableDatabaseProvider provider;
        private final CompositeDatabasePreparer preparer;

        private TemplateKey(TemplatableDatabaseProvider provider, CompositeDatabasePreparer preparer) {
            this.provider = provider;
            this.preparer = preparer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TemplateKey that = (TemplateKey) o;
            return Objects.equals(provider, that.provider) &&
                    Objects.equals(preparer, that.preparer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, preparer);
        }
    }

    private static class TemplateWrapper {

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final CompletableFuture<DatabaseTemplate> future = new CompletableFuture<>();

        private final TemplatableDatabaseProvider provider;
        private final CompositeDatabasePreparer preparer;

        private boolean closed = false;

        private TemplateWrapper(TemplatableDatabaseProvider provider, CompositeDatabasePreparer preparer) {
            this.provider = provider;
            this.preparer = preparer;
        }

        public CompositeDatabasePreparer getPreparer() {
            return preparer;
        }

        public boolean isLoaded() {
            return future.isDone();
        }

        public EmbeddedDatabase createDatabase(CompositeDatabasePreparer preparer) {
            lock.readLock().lock();
            try {
                if (!closed) {
                    return provider.createDatabase(DatabaseRequest.of(preparer, getTemplate()));
                }
            } finally {
                lock.readLock().unlock();
            }
            CompositeDatabasePreparer mergedPreparer = mergedPreparer(preparer, this);
            return provider.createDatabase(DatabaseRequest.of(mergedPreparer));
        }

        public DatabaseTemplate createTemplate(CompositeDatabasePreparer preparer) {
            lock.readLock().lock();
            try {
                if (!closed) {
                    return provider.createTemplate(DatabaseRequest.of(preparer, getTemplate()));
                }
            } finally {
                lock.readLock().unlock();
            }
            CompositeDatabasePreparer mergedPreparer = mergedPreparer(preparer, this);
            return provider.createTemplate(DatabaseRequest.of(mergedPreparer));
        }

        public void close() {
            lock.writeLock().lock();
            try {
                closed = true;
                getTemplate().close();
            } finally {
                lock.writeLock().unlock();
            }
        }

        private DatabaseTemplate getTemplate() {
            try {
                return future.get();
            } catch (ExecutionException | InterruptedException e) {
                Throwables.throwIfInstanceOf(e.getCause(), ProviderException.class);
                throw new ProviderException("Unexpected error when preparing a database template", e.getCause());
            }
        }

        private void loadTemplate(Supplier<DatabaseTemplate> templateProvider) {
            try {
                future.complete(templateProvider.get());
            } catch (Throwable e) {
                future.completeExceptionally(e);
                throw e;
            }
        }
    }

    private static class PreparerStats {

        private final AtomicLong totalLoadTime = new AtomicLong(0);
        private final AtomicInteger loadCount = new AtomicInteger(0);

        public long getTotalLoadTime() {
            return totalLoadTime.get();
        }

        public int getLoadCount() {
            return loadCount.get();
        }

        public long getAvgLoadTime() {
            return getTotalLoadTime() / getLoadCount();
        }

        public void onLoad(long loadTime) {
            loadCount.incrementAndGet();
            totalLoadTime.addAndGet(loadTime);
        }
    }

    public static class Config {

        private final long durationThreshold;
        private final int maxTemplateCount;

        private Config(Config.Builder builder) {
            this.durationThreshold = builder.durationThreshold;
            this.maxTemplateCount = builder.maxTemplateCount;
        }

        public long getDurationThreshold() {
            return durationThreshold;
        }

        public int getMaxTemplateCount() {
            return maxTemplateCount;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Config config = (Config) o;
            return durationThreshold == config.durationThreshold &&
                    maxTemplateCount == config.maxTemplateCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(durationThreshold, maxTemplateCount);
        }

        public static class Builder {

            private long durationThreshold = 0;
            private int maxTemplateCount = 10;

            private Builder() {}

            public Builder withDurationThreshold(long durationThreshold) {
                this.durationThreshold = durationThreshold;
                return this;
            }

            public Builder withMaxTemplateCount(int maxTemplateCount) {
                this.maxTemplateCount = maxTemplateCount;
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }
}
