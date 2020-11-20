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

package io.zonky.test.db.config;

import io.zonky.test.db.flyway.FlywayDatabaseExtension;
import io.zonky.test.db.flyway.FlywayPropertiesPostProcessor;
import io.zonky.test.db.liquibase.LiquibaseDatabaseExtension;
import io.zonky.test.db.liquibase.LiquibasePropertiesPostProcessor;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import io.zonky.test.db.provider.common.OptimizingDatabaseProvider;
import io.zonky.test.db.provider.common.PrefetchingDatabaseProvider;
import io.zonky.test.db.provider.common.TemplatingDatabaseProvider;
import io.zonky.test.db.provider.mariadb.DockerMariaDBDatabaseProvider;
import io.zonky.test.db.provider.mssql.DockerMSSQLDatabaseProvider;
import io.zonky.test.db.provider.mysql.DockerMySQLDatabaseProvider;
import io.zonky.test.db.provider.postgres.DockerPostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.OpenTablePostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.YandexPostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.ZonkyPostgresDatabaseProvider;
import io.zonky.test.db.support.DatabaseProviders;
import io.zonky.test.db.support.DefaultProviderResolver;
import io.zonky.test.db.support.ProviderResolver;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

@Configuration
public class EmbeddedDatabaseAutoConfiguration implements EnvironmentAware, BeanClassLoaderAware, BeanFactoryAware {

    private Environment environment;
    private ClassLoader classLoader;
    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (AutowireCapableBeanFactory) beanFactory;
    }

    @Bean
    @Provider(type = "docker", database = "postgres")
    @ConditionalOnMissingBean(name = "dockerPostgresDatabaseProvider")
    public DatabaseProvider dockerPostgresDatabaseProvider() {
        checkDependency("org.testcontainers", "postgresql", "org.testcontainers.containers.PostgreSQLContainer");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        TemplatableDatabaseProvider provider = beanFactory.createBean(DockerPostgresDatabaseProvider.class);
        return optimizingDatabaseProvider(prefetchingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "zonky", database = "postgres")
    @ConditionalOnMissingBean(name = "zonkyPostgresDatabaseProvider")
    public DatabaseProvider zonkyPostgresDatabaseProvider() {
        checkDependency("io.zonky.test", "embedded-postgres", "io.zonky.test.db.postgres.embedded.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        TemplatableDatabaseProvider provider = beanFactory.createBean(ZonkyPostgresDatabaseProvider.class);
        return optimizingDatabaseProvider(prefetchingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "opentable", database = "postgres")
    @ConditionalOnMissingBean(name = "openTablePostgresDatabaseProvider")
    public DatabaseProvider openTablePostgresDatabaseProvider() {
        checkDependency("com.opentable.components", "otj-pg-embedded", "com.opentable.db.postgres.embedded.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        TemplatableDatabaseProvider provider = beanFactory.createBean(OpenTablePostgresDatabaseProvider.class);
        return optimizingDatabaseProvider(prefetchingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "yandex", database = "postgres")
    @ConditionalOnMissingBean(name = "yandexPostgresDatabaseProvider")
    public DatabaseProvider yandexPostgresDatabaseProvider() {
        checkDependency("ru.yandex.qatools.embed", "postgresql-embedded", "ru.yandex.qatools.embed.postgresql.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        TemplatableDatabaseProvider provider = beanFactory.createBean(YandexPostgresDatabaseProvider.class);
        return optimizingDatabaseProvider(prefetchingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "docker", database = "mssql")
    @ConditionalOnMissingBean(name = "dockerMsSqlDatabaseProvider")
    public DatabaseProvider dockerMsSqlDatabaseProvider() {
        checkDependency("org.testcontainers", "mssqlserver", "org.testcontainers.containers.MSSQLServerContainer");
        checkDependency("com.microsoft.sqlserver", "mssql-jdbc", "com.microsoft.sqlserver.jdbc.SQLServerDataSource");
        DockerMSSQLDatabaseProvider provider = beanFactory.createBean(DockerMSSQLDatabaseProvider.class);
        return optimizingDatabaseProvider(prefetchingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "docker", database = "mysql")
    @ConditionalOnMissingBean(name = "dockerMySqlDatabaseProvider")
    public DatabaseProvider dockerMySqlDatabaseProvider() {
        checkDependency("org.testcontainers", "mysql", "org.testcontainers.containers.MySQLContainer");
        checkDependency("mysql", "mysql-connector-java", "com.mysql.cj.jdbc.MysqlDataSource");
        DockerMySQLDatabaseProvider provider = beanFactory.createBean(DockerMySQLDatabaseProvider.class);
        return optimizingDatabaseProvider(provider);
    }

    @Bean
    @Provider(type = "docker", database = "mariadb")
    @ConditionalOnMissingBean(name = "dockerMariaDbDatabaseProvider")
    public DatabaseProvider dockerMariaDbDatabaseProvider() {
        checkDependency("org.testcontainers", "mariadb", "org.testcontainers.containers.MariaDBContainer");
        checkDependency("org.mariadb.jdbc", "mariadb-java-client", "org.mariadb.jdbc.MariaDbDataSource");
        DockerMariaDBDatabaseProvider provider = beanFactory.createBean(DockerMariaDBDatabaseProvider.class);
        return optimizingDatabaseProvider(provider);
    }

    @Bean
    @Scope("prototype")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "optimizingDatabaseProvider")
    public OptimizingDatabaseProvider optimizingDatabaseProvider(DatabaseProvider provider) {
        return new OptimizingDatabaseProvider(provider);
    }

    // TODO: consider using a factory bean instead (also consider using pipeline factories aimed for specific provider types)
    @Bean
    @Scope("prototype")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "prefetchingDatabaseProvider")
    public PrefetchingDatabaseProvider prefetchingDatabaseProvider(DatabaseProvider provider) {
        return new PrefetchingDatabaseProvider(provider, environment);
    }

    @Bean
    @Scope("prototype")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "templatingDatabaseProvider")
    public TemplatingDatabaseProvider templatingDatabaseProvider(TemplatableDatabaseProvider provider) {
        return new TemplatingDatabaseProvider(provider);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "databaseProviders")
    public DatabaseProviders databaseProviders(ConfigurableListableBeanFactory beanFactory) {
        return new DatabaseProviders(beanFactory);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "providerResolver")
    public ProviderResolver providerResolver(Environment environment) {
        return new DefaultProviderResolver(environment, classLoader);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(name = "org.flywaydb.core.Flyway")
    @ConditionalOnMissingBean(name = "flywayDatabaseExtension")
    public FlywayDatabaseExtension flywayDatabaseExtension() {
        return new FlywayDatabaseExtension();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(name = "org.springframework.boot.autoconfigure.flyway.FlywayProperties")
    @ConditionalOnMissingBean(name = "flywayPropertiesPostProcessor")
    public FlywayPropertiesPostProcessor flywayPropertiesPostProcessor() {
        return new FlywayPropertiesPostProcessor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(name = "liquibase.integration.spring.SpringLiquibase")
    @ConditionalOnMissingBean(name = "liquibaseDatabaseExtension")
    public LiquibaseDatabaseExtension liquibaseDatabaseExtension() {
        return new LiquibaseDatabaseExtension();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(name = "org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties")
    @ConditionalOnMissingBean(name = "liquibasePropertiesPostProcessor")
    public LiquibasePropertiesPostProcessor liquibasePropertiesPostProcessor() {
        return new LiquibasePropertiesPostProcessor();
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
