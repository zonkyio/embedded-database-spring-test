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
import io.zonky.test.db.init.EmbeddedDatabaseInitializer;
import io.zonky.test.db.init.ScriptDatabasePreparer;
import io.zonky.test.db.liquibase.LiquibaseDatabaseExtension;
import io.zonky.test.db.liquibase.LiquibasePropertiesPostProcessor;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.derby.DerbyDatabaseProvider;
import io.zonky.test.db.provider.h2.H2DatabaseProvider;
import io.zonky.test.db.provider.hsqldb.HSQLDatabaseProvider;
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
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.nio.charset.Charset;
import java.util.Arrays;

@Configuration
public class EmbeddedDatabaseAutoConfiguration implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Bean
    @Provider(type = "docker", database = "postgres")
    @ConditionalOnMissingBean(name = "dockerPostgresDatabaseProvider")
    public DatabaseProvider dockerPostgresDatabaseProvider(DatabaseProviderFactory postgresDatabaseProviderFactory) {
        checkDependency("org.testcontainers", "postgresql", "org.testcontainers.containers.PostgreSQLContainer");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        return postgresDatabaseProviderFactory.createProvider(DockerPostgresDatabaseProvider.class);
    }

    @Bean
    @Provider(type = "zonky", database = "postgres")
    @ConditionalOnMissingBean(name = "zonkyPostgresDatabaseProvider")
    public DatabaseProvider zonkyPostgresDatabaseProvider(DatabaseProviderFactory postgresDatabaseProviderFactory) {
        checkDependency("io.zonky.test", "embedded-postgres", "io.zonky.test.db.postgres.embedded.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        return postgresDatabaseProviderFactory.createProvider(ZonkyPostgresDatabaseProvider.class);
    }

    @Bean
    @Provider(type = "opentable", database = "postgres")
    @ConditionalOnMissingBean(name = "openTablePostgresDatabaseProvider")
    public DatabaseProvider openTablePostgresDatabaseProvider(DatabaseProviderFactory postgresDatabaseProviderFactory) {
        checkDependency("com.opentable.components", "otj-pg-embedded", "com.opentable.db.postgres.embedded.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        return postgresDatabaseProviderFactory.createProvider(OpenTablePostgresDatabaseProvider.class);
    }

    @Bean
    @Provider(type = "yandex", database = "postgres")
    @ConditionalOnMissingBean(name = "yandexPostgresDatabaseProvider")
    public DatabaseProvider yandexPostgresDatabaseProvider(DatabaseProviderFactory postgresDatabaseProviderFactory) {
        checkDependency("ru.yandex.qatools.embed", "postgresql-embedded", "ru.yandex.qatools.embed.postgresql.EmbeddedPostgres");
        checkDependency("org.postgresql", "postgresql", "org.postgresql.ds.PGSimpleDataSource");
        return postgresDatabaseProviderFactory.createProvider(YandexPostgresDatabaseProvider.class);
    }

    @Bean
    @Provider(type = "docker", database = "mssql")
    @ConditionalOnMissingBean(name = "dockerMsSqlDatabaseProvider")
    public DatabaseProvider dockerMsSqlDatabaseProvider(DatabaseProviderFactory msSqlDatabaseProviderFactory) {
        checkDependency("org.testcontainers", "mssqlserver", "org.testcontainers.containers.MSSQLServerContainer");
        checkDependency("com.microsoft.sqlserver", "mssql-jdbc", "com.microsoft.sqlserver.jdbc.SQLServerDataSource");
        return msSqlDatabaseProviderFactory.createProvider(DockerMSSQLDatabaseProvider.class);
    }

    @Bean
    @Provider(type = "docker", database = "mysql")
    @ConditionalOnMissingBean(name = "dockerMySqlDatabaseProvider")
    public DatabaseProvider dockerMySqlDatabaseProvider(DatabaseProviderFactory mySqlDatabaseProviderFactory) {
        checkDependency("org.testcontainers", "mysql", "org.testcontainers.containers.MySQLContainer");
        checkDependency("mysql", "mysql-connector-java", "com.mysql.cj.jdbc.MysqlDataSource");
        return mySqlDatabaseProviderFactory.createProvider(DockerMySQLDatabaseProvider.class);
    }

    @Bean
    @Provider(type = "docker", database = "mariadb")
    @ConditionalOnMissingBean(name = "dockerMariaDbDatabaseProvider")
    public DatabaseProvider dockerMariaDbDatabaseProvider(DatabaseProviderFactory mariaDbDatabaseProviderFactory) {
        checkDependency("org.testcontainers", "mariadb", "org.testcontainers.containers.MariaDBContainer");
        checkDependency("org.mariadb.jdbc", "mariadb-java-client", "org.mariadb.jdbc.MariaDbDataSource");
        return mariaDbDatabaseProviderFactory.createProvider(DockerMariaDBDatabaseProvider.class);
    }

    @Bean
    @Provider(type = "embedded", database = "h2")
    @ConditionalOnMissingBean(name = "h2DatabaseProvider")
    public DatabaseProvider h2DatabaseProvider(DatabaseProviderFactory h2DatabaseProviderFactory) {
        checkDependency("com.h2database", "h2", "org.h2.Driver");
        return h2DatabaseProviderFactory.createProvider(H2DatabaseProvider.class);
    }

    @Bean
    @Provider(type = "embedded", database = "hsql")
    @ConditionalOnMissingBean(name = "hsqlDatabaseProvider")
    public DatabaseProvider hsqlDatabaseProvider(DatabaseProviderFactory hsqlDatabaseProviderFactory) {
        checkDependency("org.hsqldb", "hsqldb", "org.hsqldb.jdbcDriver");
        return hsqlDatabaseProviderFactory.createProvider(HSQLDatabaseProvider.class);
    }

    @Bean
    @Provider(type = "embedded", database = "derby")
    @ConditionalOnMissingBean(name = "derbyDatabaseProvider")
    public DatabaseProvider derbyDatabaseProvider(DatabaseProviderFactory derbyDatabaseProviderFactory) {
        if (!ClassUtils.isPresent("org.apache.derby.info.engine.DerbyModule", classLoader)) {
            checkDependency("org.apache.derby", "derby", "org.apache.derby.jdbc.EmbeddedDriver");
        } else {
            checkDependency("org.apache.derby", "derbytools", "org.apache.derby.jdbc.EmbeddedDriver");
        }
        return derbyDatabaseProviderFactory.createProvider(DerbyDatabaseProvider.class);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "postgresDatabaseProviderFactory")
    public DatabaseProviderFactory postgresDatabaseProviderFactory(DatabaseProviderFactory defaultDatabaseProviderFactory) {
        return defaultDatabaseProviderFactory.customizeProvider((builder, provider) ->
                builder.optimizingProvider(
                        builder.prefetchingProvider(
                                builder.templatingProvider(provider))));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "msSqlDatabaseProviderFactory")
    public DatabaseProviderFactory msSqlDatabaseProviderFactory(DatabaseProviderFactory defaultDatabaseProviderFactory) {
        return defaultDatabaseProviderFactory.customizeProvider((builder, provider) ->
                builder.optimizingProvider(
                        builder.prefetchingProvider(
                                builder.templatingProvider(provider))));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "mySqlDatabaseProviderFactory")
    public DatabaseProviderFactory mySqlDatabaseProviderFactory(DatabaseProviderFactory defaultDatabaseProviderFactory) {
        return defaultDatabaseProviderFactory.customizeProvider((builder, provider) ->
                builder.optimizingProvider(provider));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "mariaDbDatabaseProviderFactory")
    public DatabaseProviderFactory mariaDbDatabaseProviderFactory(DatabaseProviderFactory defaultDatabaseProviderFactory) {
        return defaultDatabaseProviderFactory.customizeProvider((builder, provider) ->
                builder.optimizingProvider(provider));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "h2DatabaseProviderFactory")
    public DatabaseProviderFactory h2DatabaseProviderFactory(DatabaseProviderFactory defaultDatabaseProviderFactory) {
        return defaultDatabaseProviderFactory.customizeProvider((builder, provider) ->
                builder.optimizingProvider(
                        builder.prefetchingProvider(provider)));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "hsqlDatabaseProviderFactory")
    public DatabaseProviderFactory hsqlDatabaseProviderFactory(DatabaseProviderFactory defaultDatabaseProviderFactory) {
        return defaultDatabaseProviderFactory.customizeProvider((builder, provider) ->
                builder.optimizingProvider(
                        builder.prefetchingProvider(provider)));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "derbyDatabaseProviderFactory")
    public DatabaseProviderFactory derbyDatabaseProviderFactory(DatabaseProviderFactory defaultDatabaseProviderFactory) {
        return defaultDatabaseProviderFactory.customizeProvider((builder, provider) ->
                builder.optimizingProvider(
                        builder.prefetchingProvider(provider)));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "defaultDatabaseProviderFactory")
    public DatabaseProviderFactory defaultDatabaseProviderFactory(AutowireCapableBeanFactory beanFactory, Environment environment) {
        String threadNamePrefix = environment.getProperty("zonky.test.database.prefetching.thread-name-prefix", "prefetching-");
        int concurrency = environment.getProperty("zonky.test.database.prefetching.concurrency", int.class, 3);
        int pipelineCacheSize = environment.getProperty("zonky.test.database.prefetching.pipeline-cache-size", int.class, 5);
        int maxPreparedTemplates = environment.getProperty("zonky.test.database.prefetching.max-prepared-templates", int.class, 10);
        int maxPreparedDatabases = (maxPreparedTemplates * 2/3 * 2) + pipelineCacheSize;

        return new DatabaseProviderFactory(beanFactory)
                .customizeTemplating(builder -> builder
                        .withMaxTemplateCount(maxPreparedTemplates))
                .customizePrefetching(builder -> builder
                        .withThreadNamePrefix(threadNamePrefix)
                        .withConcurrency(concurrency)
                        .withPipelineMaxCacheSize(pipelineCacheSize)
                        .withMaxPreparedDatabases(maxPreparedDatabases));
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

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "embeddedDatabaseInitializer")
    public EmbeddedDatabaseInitializer embeddedDatabaseInitializer(Environment environment) {
        String[] scriptLocations = environment.getProperty("zonky.test.database.init.script-locations", String[].class);
        boolean continueOnError = environment.getProperty("zonky.test.database.init.continue-on-error", boolean.class, false);
        String separator = environment.getProperty("zonky.test.database.init.separator", ";");
        Charset encoding = environment.getProperty("zonky.test.database.init.encoding", Charset.class);

        ScriptDatabasePreparer scriptPreparer = null;
        if (scriptLocations != null) {
            scriptPreparer = new ScriptDatabasePreparer(Arrays.asList(scriptLocations), continueOnError, separator, encoding);
        }
        return new EmbeddedDatabaseInitializer(scriptPreparer);
    }

    private void checkDependency(String groupId, String artifactId, String className) {
        if (!ClassUtils.isPresent(className, classLoader)) {
            String dependencyName = String.format("%s:%s", groupId, artifactId);
            String dependencyUrl = String.format("https://mvnrepository.com/artifact/%s/%s", groupId, artifactId);
            String errorMessage = String.format("You have to add the following dependency to your project: '%s' (%s)", dependencyName, dependencyUrl);
            throw new MissingProviderDependencyException(errorMessage);
        }
    }
}
