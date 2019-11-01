package io.zonky.test.db.provider.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import io.zonky.test.db.flyway.CompositeDatabasePreparer;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.DatabaseRequest;
import io.zonky.test.db.provider.DatabaseResult;
import io.zonky.test.db.provider.DatabaseTemplate;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;

import java.util.List;
import java.util.Objects;

public class OptimizingDatabaseProvider implements DatabaseProvider {

    private static final LoadingCache<TemplateKey, DatabaseTemplate> templates = CacheBuilder.newBuilder()
            .build(new CacheLoader<TemplateKey, DatabaseTemplate>() {
                public DatabaseTemplate load(TemplateKey key) throws Exception {
                    List<DatabasePreparer> preparers = ((CompositeDatabasePreparer) key.preparer).getPreparers();

                    for (int i = preparers.size() - 1; i > 0; i--) {
                        CompositeDatabasePreparer partialPreparer = new CompositeDatabasePreparer(preparers.subList(0, i));
                        TemplateKey templateKey = new TemplateKey(key.provider, partialPreparer);
                        DatabaseTemplate template = templates.getIfPresent(templateKey);
                        if (template != null) {
                            return key.provider.createTemplate(new DatabaseRequest(template, new CompositeDatabasePreparer(preparers.subList(i, preparers.size()))));
                        }
                    }

                    return key.provider.createTemplate(new DatabaseRequest(key.preparer));
                }
            });

    private final TemplatableDatabaseProvider provider;

    public OptimizingDatabaseProvider(TemplatableDatabaseProvider provider) {
        this.provider = provider;
    }

    @Override
    public DatabaseResult createDatabase(DatabasePreparer preparer) throws Exception {
        CompositeDatabasePreparer compositePreparer = preparer instanceof CompositeDatabasePreparer ?
                (CompositeDatabasePreparer) preparer : new CompositeDatabasePreparer(ImmutableList.of(preparer));

        DatabaseTemplate template = templates.get(new TemplateKey(provider, compositePreparer));
        DatabaseRequest request = new DatabaseRequest(template, null);
//        DatabaseRequest request = new DatabaseRequest(null, preparer); // TODO: implement smarter optimizer

        return provider.createDatabase(request);
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
