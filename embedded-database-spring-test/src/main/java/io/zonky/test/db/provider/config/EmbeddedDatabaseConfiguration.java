package io.zonky.test.db.provider.config;

import io.zonky.test.db.flyway.FlywayContextExtension;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.MissingProviderDependencyException;
import io.zonky.test.db.provider.impl.DockerPostgresDatabaseProvider;
import io.zonky.test.db.provider.impl.OpenTablePostgresDatabaseProvider;
import io.zonky.test.db.provider.impl.OptimizingDatabaseProvider;
import io.zonky.test.db.provider.impl.PrefetchingDatabaseProvider;
import io.zonky.test.db.provider.impl.YandexPostgresDatabaseProvider;
import io.zonky.test.db.provider.impl.ZonkyPostgresDatabaseProvider;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

@Configuration
public class EmbeddedDatabaseConfiguration implements EnvironmentAware, BeanClassLoaderAware {

    private Environment environment;
    private ClassLoader classLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Bean
    public FlywayContextExtension flywayContextExtension() {
        return new FlywayContextExtension();
    }

    @Bean
    public DatabaseProviders databaseProviders(ConfigurableListableBeanFactory beanFactory) {
        return new DatabaseProviders(beanFactory);
    }

    @Bean
    @Provider(type = "docker", database = "postgres")
    public DatabaseProvider dockerPostgresDatabaseProvider() {
        checkDependency("org.testcontainers", "postgresql", "org.testcontainers.containers.PostgreSQLContainer");
        return new PrefetchingDatabaseProvider(new OptimizingDatabaseProvider(new DockerPostgresDatabaseProvider(environment)), environment);
    }

    @Bean
    @Provider(type = "zonky", database = "postgres")
    public DatabaseProvider zonkyPostgresDatabaseProvider(AutowireCapableBeanFactory beanFactory) {
        checkDependency("io.zonky.test", "embedded-postgres", "io.zonky.test.db.postgres.embedded.EmbeddedPostgres");
        return new PrefetchingDatabaseProvider(new OptimizingDatabaseProvider(new ZonkyPostgresDatabaseProvider(environment, beanFactory)), environment);
    }

    @Bean
    @Provider(type = "opentable", database = "postgres")
    public DatabaseProvider openTablePostgresDatabaseProvider(AutowireCapableBeanFactory beanFactory) {
        checkDependency("com.opentable.components", "otj-pg-embedded", "com.opentable.db.postgres.embedded.EmbeddedPostgres");
        return new PrefetchingDatabaseProvider(new OptimizingDatabaseProvider(new OpenTablePostgresDatabaseProvider(environment, beanFactory)), environment);
    }

    @Bean
    @Provider(type = "yandex", database = "postgres")
    public DatabaseProvider yandexPostgresDatabaseProvider() {
        checkDependency("ru.yandex.qatools.embed", "postgresql-embedded", "ru.yandex.qatools.embed.postgresql.EmbeddedPostgres");
        return new PrefetchingDatabaseProvider(new OptimizingDatabaseProvider(new YandexPostgresDatabaseProvider(environment)), environment);
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
