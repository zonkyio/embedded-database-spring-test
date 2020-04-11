package io.zonky.test.db;

import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.flyway.FlywayExtension;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import io.zonky.test.db.provider.config.DatabaseProviders;
import io.zonky.test.db.provider.config.MissingProviderDependencyException;
import io.zonky.test.db.provider.config.Provider;
import io.zonky.test.db.provider.postgres.DockerPostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.OpenTablePostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.OptimizingDatabaseProvider;
import io.zonky.test.db.provider.postgres.PrefetchingDatabaseProvider;
import io.zonky.test.db.provider.postgres.YandexPostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.ZonkyPostgresDatabaseProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.util.List;

@Configuration
public class EmbeddedDatabaseConfiguration implements EnvironmentAware, BeanClassLoaderAware, BeanFactoryAware {

    private Environment environment;
    private ClassLoader classLoader;
    private AutowireCapableBeanFactory beanFactory;

    @Autowired
    private List<DataSourceContext> contexts;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (AutowireCapableBeanFactory) beanFactory;
    }

    @Bean
    public FlywayExtension flywayContextExtension() {
        return new FlywayExtension();
    }

    @Bean
    public DatabaseProviders databaseProviders(ConfigurableListableBeanFactory beanFactory) {
        return new DatabaseProviders(beanFactory);
    }

    @Bean
    @Provider(type = "docker", database = "postgres")
    public DatabaseProvider dockerPostgresDatabaseProvider() {
        checkDependency("org.testcontainers", "postgresql", "org.testcontainers.containers.PostgreSQLContainer");
        TemplatableDatabaseProvider provider = beanFactory.createBean(DockerPostgresDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(provider));
    }

    @Bean
    @Provider(type = "zonky", database = "postgres")
    public DatabaseProvider zonkyPostgresDatabaseProvider() {
        checkDependency("io.zonky.test", "embedded-postgres", "io.zonky.test.db.postgres.embedded.EmbeddedPostgres");
        TemplatableDatabaseProvider provider = beanFactory.createBean(ZonkyPostgresDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(provider));
    }

    @Bean
    @Provider(type = "opentable", database = "postgres")
    public DatabaseProvider openTablePostgresDatabaseProvider() {
        checkDependency("com.opentable.components", "otj-pg-embedded", "com.opentable.db.postgres.embedded.EmbeddedPostgres");
        TemplatableDatabaseProvider provider = beanFactory.createBean(OpenTablePostgresDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(provider));
    }

    @Bean
    @Provider(type = "yandex", database = "postgres")
    public DatabaseProvider yandexPostgresDatabaseProvider() {
        checkDependency("ru.yandex.qatools.embed", "postgresql-embedded", "ru.yandex.qatools.embed.postgresql.EmbeddedPostgres");
        TemplatableDatabaseProvider provider = beanFactory.createBean(YandexPostgresDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(provider));
    }

    @Bean
    @Scope("prototype")
    public OptimizingDatabaseProvider optimizingDatabaseProvider(TemplatableDatabaseProvider provider) {
        return new OptimizingDatabaseProvider(provider, contexts);
    }

    @Bean
    @Scope("prototype")
    public PrefetchingDatabaseProvider prefetchingDatabaseProvider(DatabaseProvider provider) {
        return new PrefetchingDatabaseProvider(provider, environment);
    }

    private void checkDependency(String groupId, String artifactId, String className) {
        if (!ClassUtils.isPresent(className, classLoader)) {
            String dependencyName = String.format("%s:%s", groupId, artifactId);
            String dependencyUrl = String.format("https://mvnrepository.com/artifact/%s/%s", groupId, artifactId);
            String errorMessage = String.format("You need to add the following dependency: '%s' (%s)", dependencyName, dependencyUrl);
            throw new MissingProviderDependencyException(errorMessage);
        }
    }
}
