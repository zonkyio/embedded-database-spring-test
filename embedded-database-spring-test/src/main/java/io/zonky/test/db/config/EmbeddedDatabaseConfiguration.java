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

import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.flyway.FlywayExtension;
import io.zonky.test.db.flyway.FlywayPropertiesPostProcessor;
import io.zonky.test.db.liquibase.LiquibaseExtension;
import io.zonky.test.db.liquibase.LiquibasePropertiesPostProcessor;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.DatabaseProviders;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import io.zonky.test.db.provider.mariadb.DockerMariaDBDatabaseProvider;
import io.zonky.test.db.provider.mssql.DockerMSSQLDatabaseProvider;
import io.zonky.test.db.provider.mysql.DockerMySQLDatabaseProvider;
import io.zonky.test.db.provider.postgres.DockerPostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.OpenTablePostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.OptimizingDatabaseProvider;
import io.zonky.test.db.provider.postgres.PrefetchingDatabaseProvider;
import io.zonky.test.db.provider.postgres.TemplatingDatabaseProvider;
import io.zonky.test.db.provider.postgres.YandexPostgresDatabaseProvider;
import io.zonky.test.db.provider.postgres.ZonkyPostgresDatabaseProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
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
    public DatabaseProviders databaseProviders(ConfigurableListableBeanFactory beanFactory) {
        return new DatabaseProviders(beanFactory);
    }

    @Bean
    @Provider(type = "docker", database = "postgres")
    public DatabaseProvider dockerPostgresDatabaseProvider() {
        checkDependency("org.testcontainers", "postgresql", "org.testcontainers.containers.PostgreSQLContainer");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        TemplatableDatabaseProvider provider = beanFactory.createBean(DockerPostgresDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "zonky", database = "postgres")
    public DatabaseProvider zonkyPostgresDatabaseProvider() {
        checkDependency("io.zonky.test", "embedded-postgres", "io.zonky.test.db.postgres.embedded.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        TemplatableDatabaseProvider provider = beanFactory.createBean(ZonkyPostgresDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "opentable", database = "postgres")
    public DatabaseProvider openTablePostgresDatabaseProvider() {
        checkDependency("com.opentable.components", "otj-pg-embedded", "com.opentable.db.postgres.embedded.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        TemplatableDatabaseProvider provider = beanFactory.createBean(OpenTablePostgresDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "yandex", database = "postgres")
    public DatabaseProvider yandexPostgresDatabaseProvider() {
        checkDependency("ru.yandex.qatools.embed", "postgresql-embedded", "ru.yandex.qatools.embed.postgresql.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        TemplatableDatabaseProvider provider = beanFactory.createBean(YandexPostgresDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "docker", database = "mssql")
    public DatabaseProvider dockerMsSqlDatabaseProvider() {
        checkDependency("org.testcontainers", "mssqlserver", "org.testcontainers.containers.MSSQLServerContainer");
        checkDependency("com.microsoft.sqlserver", "mssql-jdbc", "com.microsoft.sqlserver.jdbc.SQLServerDataSource");
        DockerMSSQLDatabaseProvider provider = beanFactory.createBean(DockerMSSQLDatabaseProvider.class);
        return prefetchingDatabaseProvider(optimizingDatabaseProvider(templatingDatabaseProvider(provider)));
    }

    @Bean
    @Provider(type = "docker", database = "mysql")
    public DatabaseProvider dockerMySqlDatabaseProvider() {
        checkDependency("org.testcontainers", "mysql", "org.testcontainers.containers.MySQLContainer");
        checkDependency("mysql", "mysql-connector-java", "com.mysql.cj.jdbc.MysqlDataSource");
        DockerMySQLDatabaseProvider provider = beanFactory.createBean(DockerMySQLDatabaseProvider.class);
        return optimizingDatabaseProvider(provider);
    }

    @Bean
    @Provider(type = "docker", database = "mariadb")
    public DatabaseProvider dockerMariaDbDatabaseProvider() {
        checkDependency("org.testcontainers", "mariadb", "org.testcontainers.containers.MariaDBContainer");
        checkDependency("org.mariadb.jdbc", "mariadb-java-client", "org.mariadb.jdbc.MariaDbDataSource");
        DockerMariaDBDatabaseProvider provider = beanFactory.createBean(DockerMariaDBDatabaseProvider.class);
        return optimizingDatabaseProvider(provider);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Scope("prototype")
    public PrefetchingDatabaseProvider prefetchingDatabaseProvider(DatabaseProvider provider) {
        return new PrefetchingDatabaseProvider(provider, environment);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Scope("prototype")
    public OptimizingDatabaseProvider optimizingDatabaseProvider(DatabaseProvider provider) {
        return new OptimizingDatabaseProvider(provider);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Scope("prototype")
    public TemplatingDatabaseProvider templatingDatabaseProvider(TemplatableDatabaseProvider provider) {
        return new TemplatingDatabaseProvider(provider, contexts);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(name = "org.flywaydb.core.Flyway")
    public FlywayExtension flywayExtension() {
        return new FlywayExtension();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(name = "org.springframework.boot.autoconfigure.flyway.FlywayProperties")
    public FlywayPropertiesPostProcessor flywayPropertiesPostProcessor() {
        return new FlywayPropertiesPostProcessor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(name = "liquibase.integration.spring.SpringLiquibase")
    public LiquibaseExtension liquibaseExtension() {
        return new LiquibaseExtension();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(name = "org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties")
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
