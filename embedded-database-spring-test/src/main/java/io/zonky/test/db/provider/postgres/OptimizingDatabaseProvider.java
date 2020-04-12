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

package io.zonky.test.db.provider.postgres;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.DatabaseRequest;
import io.zonky.test.db.provider.DatabaseTemplate;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static io.zonky.test.db.context.DataSourceContext.State.INITIALIZING;

public class OptimizingDatabaseProvider implements DatabaseProvider {

    public static final DatabasePreparer EMPTY_PREPARER = new CompositeDatabasePreparer(Collections.emptyList());

    private static final Cache<TemplateKey, DatabaseTemplate> templates = CacheBuilder.newBuilder().build();

    private final TemplatableDatabaseProvider provider;
    private final List<DataSourceContext> contexts;

    public OptimizingDatabaseProvider(TemplatableDatabaseProvider provider, List<DataSourceContext> contexts) {
        this.provider = provider;
        this.contexts = ImmutableList.copyOf(contexts);
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
        CompositeDatabasePreparer compositePreparer = preparer instanceof CompositeDatabasePreparer ?
                (CompositeDatabasePreparer) preparer : new CompositeDatabasePreparer(ImmutableList.of(preparer));

        List<DatabasePreparer> preparers = compositePreparer.getPreparers();

        for (int i = preparers.size(); i > 0; i--) {
            CompositeDatabasePreparer templateKey = new CompositeDatabasePreparer(preparers.subList(0, i));
            DatabaseTemplate existingTemplate = templates.getIfPresent(new TemplateKey(provider, templateKey));
            if (existingTemplate != null) {
                CompositeDatabasePreparer complementaryPreparer = new CompositeDatabasePreparer(preparers.subList(i, preparers.size()));
                if (i == preparers.size() || isInitialized()) {
                    return provider.createDatabase(DatabaseRequest.of(complementaryPreparer, existingTemplate));
                } else {
                    DatabaseTemplate specificTemplate = createTemplate(compositePreparer, DatabaseRequest.of(complementaryPreparer, existingTemplate));
                    return provider.createDatabase(DatabaseRequest.of(EMPTY_PREPARER, specificTemplate));
                }
            }
        }

        DatabaseTemplate newTemplate = createTemplate(compositePreparer, DatabaseRequest.of(compositePreparer));
        return provider.createDatabase(DatabaseRequest.of(EMPTY_PREPARER, newTemplate));
    }

    private DatabaseTemplate createTemplate(CompositeDatabasePreparer key, DatabaseRequest request) throws ProviderException {
        try {
            return templates.get(new TemplateKey(provider, key), () -> provider.createTemplate(request));
        } catch (ExecutionException | UncheckedExecutionException e) {
            Throwables.throwIfInstanceOf(e.getCause(), ProviderException.class);
            throw new ProviderException("Unexpected error occurred while preparing a template", e.getCause());
        }
    }

    private boolean isInitialized() {
        return contexts.stream().noneMatch(context -> context.getState() == INITIALIZING);
    }

    protected static class TemplateKey {

        private final TemplatableDatabaseProvider provider;
        private final DatabasePreparer preparer;

        private TemplateKey(TemplatableDatabaseProvider provider, DatabasePreparer preparer) {
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
}
