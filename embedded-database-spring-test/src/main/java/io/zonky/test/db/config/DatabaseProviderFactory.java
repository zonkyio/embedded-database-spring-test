package io.zonky.test.db.config;

import com.google.common.collect.ImmutableList;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import io.zonky.test.db.provider.common.OptimizingDatabaseProvider;
import io.zonky.test.db.provider.common.PrefetchingDatabaseProvider;
import io.zonky.test.db.provider.common.TemplatingDatabaseProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.util.Assert;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class DatabaseProviderFactory {

    protected final AutowireCapableBeanFactory beanFactory;
    protected final BiFunction<PipelineBuilder, DatabaseProvider, DatabaseProvider> providerPreparer;
    protected final ImmutableList<Consumer<TemplatingDatabaseProvider.Config.Builder>> templatingCustomizers;
    protected final ImmutableList<Consumer<PrefetchingDatabaseProvider.Config.Builder>> prefetchingCustomizers;

    public DatabaseProviderFactory(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        this.providerPreparer = (builder, provider) -> provider;
        this.templatingCustomizers = ImmutableList.of();
        this.prefetchingCustomizers = ImmutableList.of();
    }

    protected DatabaseProviderFactory(AutowireCapableBeanFactory beanFactory,
                                      BiFunction<PipelineBuilder, DatabaseProvider, DatabaseProvider> providerPreparer,
                                      ImmutableList<Consumer<TemplatingDatabaseProvider.Config.Builder>> templatingCustomizers,
                                      ImmutableList<Consumer<PrefetchingDatabaseProvider.Config.Builder>> prefetchingCustomizers) {
        this.beanFactory = beanFactory;
        this.providerPreparer = providerPreparer;
        this.templatingCustomizers = templatingCustomizers;
        this.prefetchingCustomizers = prefetchingCustomizers;
    }

    public DatabaseProvider createProvider(Class<? extends DatabaseProvider> providerType) {
        return providerPreparer.apply(new PipelineBuilder(), beanFactory.createBean(providerType));
    }

    public DatabaseProviderFactory customizeTemplating(Consumer<TemplatingDatabaseProvider.Config.Builder> customizer) {
        ImmutableList<Consumer<TemplatingDatabaseProvider.Config.Builder>> templatingCustomizers =
                ImmutableList.<Consumer<TemplatingDatabaseProvider.Config.Builder>>builder()
                        .addAll(this.templatingCustomizers).add(customizer).build();
        return new DatabaseProviderFactory(beanFactory, providerPreparer, templatingCustomizers, prefetchingCustomizers);
    }

    public DatabaseProviderFactory customizePrefetching(Consumer<PrefetchingDatabaseProvider.Config.Builder> customizer) {
        ImmutableList<Consumer<PrefetchingDatabaseProvider.Config.Builder>> prefetchingCustomizers =
                ImmutableList.<Consumer<PrefetchingDatabaseProvider.Config.Builder>>builder()
                        .addAll(this.prefetchingCustomizers).add(customizer).build();
        return new DatabaseProviderFactory(beanFactory, providerPreparer, templatingCustomizers, prefetchingCustomizers);
    }

    public DatabaseProviderFactory customizeProvider(BiFunction<PipelineBuilder, DatabaseProvider, DatabaseProvider> customizer) {
        return new DatabaseProviderFactory(beanFactory, customizer, templatingCustomizers, prefetchingCustomizers);
    }

    protected OptimizingDatabaseProvider optimizingProvider(DatabaseProvider provider) {
        return new OptimizingDatabaseProvider(provider);
    }

    protected PrefetchingDatabaseProvider prefetchingProvider(DatabaseProvider provider) {
        return new PrefetchingDatabaseProvider(provider, prefetchingConfig());
    }

    protected TemplatingDatabaseProvider templatingProvider(DatabaseProvider provider) {
        Assert.isAssignable(TemplatableDatabaseProvider.class, provider.getClass());
        return new TemplatingDatabaseProvider((TemplatableDatabaseProvider) provider, templatingConfig());
    }

    protected PrefetchingDatabaseProvider.Config prefetchingConfig() {
        PrefetchingDatabaseProvider.Config.Builder builder = PrefetchingDatabaseProvider.Config.builder();
        prefetchingCustomizers.forEach(customizer -> customizer.accept(builder));
        return builder.build();
    }

    protected TemplatingDatabaseProvider.Config templatingConfig() {
        TemplatingDatabaseProvider.Config.Builder builder = TemplatingDatabaseProvider.Config.builder();
        templatingCustomizers.forEach(customizer -> customizer.accept(builder));
        return builder.build();
    }

    public class PipelineBuilder {

        public OptimizingDatabaseProvider optimizingProvider(DatabaseProvider provider) {
            return DatabaseProviderFactory.this.optimizingProvider(provider);
        }

        public PrefetchingDatabaseProvider prefetchingProvider(DatabaseProvider provider) {
            return DatabaseProviderFactory.this.prefetchingProvider(provider);
        }

        public TemplatingDatabaseProvider templatingProvider(DatabaseProvider provider) {
            return DatabaseProviderFactory.this.templatingProvider(provider);
        }
    }
}
