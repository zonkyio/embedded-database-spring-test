# <img src="zonky.jpg" height="100"> Embedded Database

## Introduction

The primary goal of this project is to make it easier to write Spring-powered integration tests that rely on `PostgreSQL`, `MSSQL`, `MySQL` or `MariaDB` database. This library is responsible for creating and managing isolated embedded databases for each test class or test method, based on a test configuration.

## Features

* Automatic integration with Spring TestContext framework
    * With a focus on context caching and database reuse
* Supports both `Spring` and `Spring Boot` frameworks
    * Supported versions are Spring 4.3.8+ and Spring Boot 1.4.6+
* Supports multiple different databases
    * [PostgreSQL](#postgresql), [MSSQL](#microsoft-sql-server), [MySQL](#mysql), [MariaDB](#mariadb)
* Supports multiple database providers
    * [Docker / Testcontainers](#using-docker-provider-default), [Zonky](#using-zonky-provider-previous-default), [OpenTable](#using-opentable-provider), [Yandex](#using-yandex-provider)
* Supports various database migration tools
    * [Flyway](#flyway), [Liquibase](#liquibase), [Spring `@Sql` annotation](#using-spring-sql-annotation) and others
* Uses optimized refreshing of embedded databases between tests
    * Database templates are used to reduce the refreshing time

## Upgrading from Embedded Database 1.x

If you are upgrading from the `1.x` version, check the [release notes](https://github.com/zonkyio/embedded-database-spring-test/wiki/Embedded-Database-2.0-Release-Notes).
You’ll find [upgrade instructions](https://github.com/zonkyio/embedded-database-spring-test/wiki/Embedded-Database-2.0-Release-Notes#upgrading-from-embedded-database-1x) along with a list of [new and noteworthy](https://github.com/zonkyio/embedded-database-spring-test/wiki/Embedded-Database-2.0-Release-Notes#new-and-noteworthy) features.

The main changes in `2.0.0` include:

- Docker as a default provider (but you can still switch to the previous [Zonky](#using-zonky-provider-previous-default) provider)
- optional Flyway dependencies
- upgrade to PostgreSQL 11
- new refresh mode
- and much more

## Quick Start

### Maven Configuration

Add the following Maven dependency:

```xml
<dependency>
    <groupId>io.zonky.test</groupId>
    <artifactId>embedded-database-spring-test</artifactId>
    <version>2.1.2</version>
    <scope>test</scope>
</dependency>
```

Also, make sure your application contains a dependency with the database driver for one of the [supported databases](#supported-databases).

### Basic Usage

The configuration of the embedded database is driven by `@AutoConfigureEmbeddedDatabase` annotation. Just put the annotation on a test class and that's it! The existing data source will be replaced with the testing one, or a new data source will be created.

## Examples

### Creating a new empty database

A new data source will be created and injected into all related components. You can also inject it into a test class as shown below. 

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
public class EmptyDatabaseIntegrationTest {
    
    @Autowired
    private DataSource dataSource;
    
    // class body...
}
```

### Replacing an existing data source with an empty database

In case the test class uses a spring context that already contains a data source bean, the data source bean will be automatically replaced with a testing data source.
The newly created data source bean will be injected into all related components, and you can also inject it into a test class.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("path/to/application-config.xml")
public class EmptyDatabaseIntegrationTest {
    // class body...
}
```

Please note that if the context contains multiple data sources the bean name must be specified using `@AutoConfigureEmbeddedDatabase(beanName = "dataSource")` to identify the data source that will be replaced.

### Creating multiple databases within a single test class

The `@AutoConfigureEmbeddedDatabase` is a repeatable annotation, so you can annotate a test class with multiple annotations to create multiple independent databases.
Each of them may have completely different configuration parameters, including the database provider as demonstrated in the example below.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource1")
@AutoConfigureEmbeddedDatabase(beanName = "dataSource2", provider = ZONKY)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource3", provider = YANDEX)
public class MultipleDatabasesIntegrationTest {
    
    @Autowired
    private DataSource dataSource1;
    @Autowired
    private DataSource dataSource2;
    @Autowired
    private DataSource dataSource3;
    
    // class body...
}
```

Note that if multiple annotations on a single class are applied, some optimization techniques cannot be used and database initialization or refresh may be slower.

### Refreshing the database during tests

The [refresh mode](#refresh-mode) feature allows you to reset the database to the initial state that existed before the test was being started.
You can choose whether the database will be refreshed only between test classes or for each test method,
and whether the refresh should take place before or after the test execution.

Please note that by default, if you do not specify refresh mode explicitly, all tests in the project share the same database. 

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(refresh = AFTER_EACH_TEST_METHOD)
public class EmptyDatabaseIntegrationTest {

        @Test
        public void testMethod1() {
            // fresh database
        }

        @Test
        public void testMethod2() {
            // fresh database
        }
}
```

Note that refresh mode can be combined with `@FlywayTest`, Spring `@Sql` or Spring Boot's annotations without any negative impact on performance.

### Using `@DataJpaTest` or `@JdbcTest` annotation

Spring Boot provides several annotations to simplify writing integration tests.
One of them is the `@DataJpaTest` annotation, which can be used when a test focuses only on JPA components.
By default, tests annotated with this annotation use an in-memory database.
But if the `@DataJpaTest` annotation is used together with the `@AutoConfigureEmbeddedDatabase` annotation,
the in-memory database is automatically disabled and replaced with an embedded database. 

```java
@RunWith(SpringRunner.class)
@DataJpaTest
@AutoConfigureEmbeddedDatabase
public class SpringDataJpaAnnotationTest {
    // class body...
}
```

You can also consider creating a custom [composed annotation](https://github.com/spring-projects/spring-framework/wiki/Spring-Annotation-Programming-Model#composed-annotations).

<details>
  <summary>Example of composed annotation</summary>
  
  ```java
  @Documented
  @Inherited
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @DataJpaTest
  @AutoConfigureEmbeddedDatabase(type = POSTGRES)
  public @interface PostgresDataJpaTest {
  
      @AliasFor(annotation = DataJpaTest.class)
      boolean showSql() default true;
  
      @AliasFor(annotation = DataJpaTest.class)
      boolean useDefaultFilters() default true;
  
      @AliasFor(annotation = DataJpaTest.class)
      Filter[] includeFilters() default {};
  
      @AliasFor(annotation = DataJpaTest.class)
      Filter[] excludeFilters() default {};
  
      @AliasFor(annotation = DataJpaTest.class)
      Class<?>[] excludeAutoConfiguration() default {};
  
  }
  ```

</details>

### Using Spring `@Sql` annotation

Spring provides the `@Sql` annotation that can be used to annotate a test class or test method to configure sql scripts to be run against an embedded database during tests.

```java
@RunWith(SpringRunner.class)
@Sql({"/test-schema.sql", "/test-user-data.sql"})
@AutoConfigureEmbeddedDatabase
public class SpringSqlAnnotationTest {
    // class body...
}
```

Note that the `@Sql` annotation can be combined with [refresh mode](#refresh-mode).

### Using `@FlywayTest` annotation

The library supports the use of `@FlywayTest` annotation. If you use it, the embedded database will be automatically initialized or cleaned by Flyway. If you don't specify any custom migration locations the default path `db/migration` will be applied.

Please note that if you put the annotation on a class, all tests within the class share the same database. If you want all the tests to be isolated, you have to put the `@FlywayTest` annotation on each test method separately.
Alternatively, you can also use [refresh mode](#refresh-mode).

```java
@RunWith(SpringRunner.class)
@FlywayTest
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("path/to/application-config.xml")
public class FlywayMigrationIntegrationTest {
    // class body...
}
```

See [Usage of Annotation FlywayTest](https://github.com/flyway/flyway-test-extensions/wiki/Usage-of-Annotation-FlywayTest) for more information about configuration options of `@FlywayTest` annotation.

## Supported Databases

### PostgreSQL

Before you use PostgreSQL database, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.2.18</version>
</dependency>
```

Note that the associated database providers support all available features
such as template databases and database prefetching, which provides maximum performance.

### Microsoft SQL Server

Before you use Microsoft SQL Server, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <version>8.4.1.jre8</version>
</dependency>
```

**In the next step, you will need to accept an EULA for using the docker image.**
See the instructions here: https://www.testcontainers.org/modules/databases/mssqlserver.

Note that the associated database provider supports all available features
such as template databases and database prefetching.
But because Microsoft SQL Server supports only a single template database per database instance,
the template databases are emulated using backup and restore operations.
However, this should have a minimal impact on the resulting performance.

### MySQL

Before you use MySQL database, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.22</version>
</dependency>
```

**Note that the associated database provider supports only basic features**
**that are required to work the embedded database properly.**
**So you may notice some performance degradation compared to other database providers.**
It's because in MySQL, `database` is only synonymous with `schema`,
which makes it hard to implement database prefetching.
Template databases are also not supported,
and cannot be easily emulated because MySQL does not support fast binary backups.

### MariaDB

Before you use MariaDB database, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>org.mariadb.jdbc</groupId>
    <artifactId>mariadb-java-client</artifactId>
    <version>2.7.0</version>
</dependency>
```

**Note that the associated database provider supports only basic features**
**that are required to work the embedded database properly.**
**So you may notice some performance degradation compared to other database providers.**
It's because in MariaDB, `database` is only synonymous with `schema`,
which makes it hard to implement database prefetching.
Template databases are also not supported,
and cannot be easily emulated because MariaDB does not support fast binary backups.

## Supported Migration Tools

Note that although any migration tool is supported,
Flyway and Liquibase provide the best performance
because the embedded database includes extra optimizations for them.

### Flyway

Before you use Flyway, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>7.3.0</version>
</dependency>
```

Optionally, you may also add the dependency for Flyway test extensions, which allows you to use the `@FlywayTest` annotation.

```xml
<dependency>
    <groupId>org.flywaydb.flyway-test-extensions</groupId>
    <artifactId>flyway-spring-test</artifactId>
    <version>7.0.0</version>
    <scope>test</scope>
</dependency>
```

Note that the processing of the `@FlywayTest` annotation is internally optimized and, if possible,
database prefetching and template databases are used to speed up applying new migrations after cleaning the database.
This operation is internally identical to the use of [refresh mode](#refresh-mode),
which can be used to replace or can be combined with the `@FlywayTest` annotation without any negative impact on performance.

### Liquibase

Before you use Liquibase, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
    <version>3.10.3</version>
</dependency>
```

Given that Liquibase does not offer an analogy to `@FlywayTest` annotation,
you may consider using [refresh mode](#refresh-mode) to refresh an embedded database during tests.

## Database Providers

The library can be combined with different database providers.
Each of them has its advantages and disadvantages summarized in the table below.

Docker provides the greatest flexibility, but it can be slightly slower than the native versions.
However, the change of the database provider is really easy, so you can try them all.

You can either configure a provider for each class separately via `@AutoConfigureEmbeddedDatabase(provider = ...)` annotation,
or using `zonky.test.database.provider` configuration property globally.

|                                   |       [Docker][docker-provider]      |             [Zonky][zonky-provider]             | [OpenTable][opentable-provider] | [Yandex][yandex-provider] |
|:---------------------------------:|:------------------------------------:|:-----------------------------------------------:|:-------------------------------:|:-------------------------:|
|          **Startup Time**         |            Slightly slower           |                       Fast                      |               Fast              | Slow, depends on platform |
|          **Performance**          | Slightly slower, depends on platform |                      Native                     |              Native             |           Native          |
|      **Supported Databases**      |   PostgreSQL, MSSQL, MySQL, MariaDB  |                    PostgreSQL                   |            PostgreSQL           |         PostgreSQL        |
|      **Supported Platforms**      |        All supported by Docker       |       Mac OS, Windows, Linux, Alpine Linux      |      Mac OS, Windows, Linux     |   Mac OS, Windows, Linux  |
|    **Supported Architectures**    |            Based on image            | amd64, i386, arm32v6, arm32v7, arm64v8, ppc64le |              amd64              |           amd64           |
| **Configurable Database Version** |            Yes, at runtime           |               Yes, at compile time              |                No               |      Yes, at runtime      |
|      **Alpine Linux Support**     |                  Yes                 |                       Yes                       |                No               |             No            |
|       **Extension Support**       |                  Yes                 |                        No                       |                No               |             No            |
|       **In-Memory Support**       |                  Yes                 |                        No                       |                No               |             No            |

[docker-provider]: https://www.testcontainers.org
[zonky-provider]: https://github.com/zonkyio/embedded-postgres
[opentable-provider]: https://github.com/opentable/otj-pg-embedded
[yandex-provider]: https://github.com/yandex-qatools/postgresql-embedded

### Common Configuration

The `@AutoConfigureEmbeddedDatabase` annotation can be used for some basic configuration, advanced configuration requires properties or yaml files.

```properties
zonky.test.database.type=auto                     # The type of embedded database to be created when replacing the data source.
zonky.test.database.provider=default              # Provider to be used to create the underlying embedded database.
zonky.test.database.refresh=never                 # Determines the refresh mode of the embedded database.
zonky.test.database.replace=any                   # Determines what type of existing DataSource beans can be replaced.

zonky.test.database.postgres.client.properties.*= # Additional PostgreSQL options used to configure the test data source.
zonky.test.database.mssql.client.properties.*=    # Additional MSSQL options used to configure the test data source.
zonky.test.database.mysql.client.properties.*=    # Additional MySQL options used to configure the test data source.
zonky.test.database.mariadb.client.properties.*=  # Additional MariaDB options used to configure the test data source.
```

Note that the library includes [configuration metadata](embedded-database-spring-test/src/main/resources/META-INF/spring-configuration-metadata.json) that offer contextual help and code completion as users are working with Spring Boot's `application.properties` or `application.yml` files.

### PostgreSQL Configuration

The following configuration keys are honored by all postgres providers:

```properties
zonky.test.database.postgres.initdb.properties.*= # Additional PostgreSQL options to pass to initdb command during the database initialization.
zonky.test.database.postgres.server.properties.*= # Additional PostgreSQL options used to configure the embedded database server.
```

**Example configuration:**
```properties
zonky.test.database.postgres.client.properties.stringtype=unspecified
zonky.test.database.postgres.initdb.properties.lc-collate=cs_CZ.UTF-8
zonky.test.database.postgres.initdb.properties.lc-monetary=cs_CZ.UTF-8
zonky.test.database.postgres.initdb.properties.lc-numeric=cs_CZ.UTF-8
zonky.test.database.postgres.server.properties.shared_buffers=512MB
zonky.test.database.postgres.server.properties.max_connections=100
```

### Using Docker Provider (default)

This is the default provider, so you do not have to do anything special,
just use the `@AutoConfigureEmbeddedDatabase` annotation in its basic form without specifying any provider.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
public class DefaultProviderIntegrationTest {
    // class body...
}
```

Note that Docker provider is especially useful if you need to use some database extensions.
Or if you want to prepare a custom docker image containing some test data.
In such cases, you can use any docker image that's compatible with the official docker images of the supported databases.

#### Docker-provider specific configuration

The provider configuration can be managed via properties in the
`zonky.test.database.postgres.docker`, `zonky.test.database.mysql.docker`, `zonky.test.database.mariadb.docker`
and `zonky.test.database.mssql.docker` groups as shown below.

```properties
zonky.test.database.postgres.docker.image=postgres:11-alpine        # Docker image containing PostgreSQL database.
zonky.test.database.postgres.docker.tmpfs.enabled=false             # Whether to mount postgres data directory as tmpfs.
zonky.test.database.postgres.docker.tmpfs.options=rw,noexec,nosuid  # Mount options used to configure the tmpfs filesystem.

zonky.test.database.mysql.docker.image=mysql:5.7                    # Docker image containing MySQL database.
zonky.test.database.mysql.docker.tmpfs.enabled=false                # Whether to mount database data directory as tmpfs.
zonky.test.database.mysql.docker.tmpfs.options=rw,noexec,nosuid     # Mount options used to configure the tmpfs filesystem.

zonky.test.database.mariadb.docker.image=mariadb:10.4               # Docker image containing MariaDB database.
zonky.test.database.mariadb.docker.tmpfs.enabled=false              # Whether to mount database data directory as tmpfs.
zonky.test.database.mariadb.docker.tmpfs.options=rw,noexec,nosuid   # Mount options used to configure the tmpfs filesystem.

zonky.test.database.mssql.docker.image=mcr.microsoft.com/mssql/server:2017-latest # Docker image containing MSSQL database.
``` 

Or, the provider configuration can also be customized with bean implementing `PostgreSQLContainerCustomizer` interface.

```java
import io.zonky.test.db.config.PostgreSQLContainerCustomizer;

@Configuration
public class EmbeddedPostgresConfiguration {
    
    @Bean
    public PostgreSQLContainerCustomizer postgresContainerCustomizer() {
        return container -> container.withStartupTimeout(Duration.ofSeconds(60L));
    }
}
```

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ContextConfiguration(classes = EmbeddedPostgresConfiguration.class)
public class EmbeddedPostgresIntegrationTest {
    // class body...
}
```

### Using Zonky Provider (previous default)

Before you use Zonky provider, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>io.zonky.test</groupId>
    <artifactId>embedded-postgres</artifactId>
    <version>1.2.10</version>
    <scope>test</scope>
</dependency>
```

Then, you can use `@AutoConfigureEmbeddedDatabase` annotation to set up the `DatabaseProvider.ZONKY` provider.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(provider = ZONKY)
public class ZonkyProviderIntegrationTest {
    // class body...
}
```

Note that Zonky provider can be useful as an alternative if you can't use Docker for some reason,
and in version `1.x.x` it used to be the default provider.

#### Changing the version of postgres binaries

The version of the binaries is configurable at compile time by importing `embedded-postgres-binaries-bom` in a required version into your dependency management section.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.zonky.test.postgres</groupId>
            <artifactId>embedded-postgres-binaries-bom</artifactId>
            <version>13.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

More information about this topic here: https://github.com/zonkyio/embedded-postgres#postgres-version

#### Enabling support for additional architectures

By default, only the support for `amd64` architecture is enabled.
Support for other architectures can be enabled using the following instructions:
https://github.com/zonkyio/embedded-postgres#additional-architectures

#### Zonky-provider specific configuration

The provider configuration can be customized with bean implementing `Consumer<EmbeddedPostgres.Builder>` interface.
The obtained builder provides methods to change the configuration before the database is being started.

```java
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

@Configuration
public class EmbeddedPostgresConfiguration {
    
    @Bean
    public Consumer<EmbeddedPostgres.Builder> embeddedPostgresCustomizer() {
        return builder -> builder.setPGStartupWait(Duration.ofSeconds(60L));
    }
}
```

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(provider = ZONKY)
@ContextConfiguration(classes = EmbeddedPostgresConfiguration.class)
public class EmbeddedPostgresIntegrationTest {
    // class body...
}
```

### Using OpenTable Provider

Before you use OpenTable provider, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>com.opentable.components</groupId>
    <artifactId>otj-pg-embedded</artifactId>
    <version>0.13.1</version>
    <scope>test</scope>
</dependency>
```

Then, you can use `@AutoConfigureEmbeddedDatabase` annotation to set up the `DatabaseProvider.OPENTABLE` provider.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(provider = OPENTABLE)
public class OpenTableProviderIntegrationTest {
    // class body...
}
```

#### OpenTable-provider specific configuration

The provider configuration can be customized with bean implementing `Consumer<EmbeddedPostgres.Builder>` interface.
The obtained builder provides methods to change the configuration before the database is being started.

```java
import com.opentable.db.postgres.embedded.EmbeddedPostgres;

@Configuration
public class EmbeddedPostgresConfiguration {
    
    @Bean
    public Consumer<EmbeddedPostgres.Builder> embeddedPostgresCustomizer() {
        return builder -> builder.setPGStartupWait(Duration.ofSeconds(60L));
    }
}
```

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(provider = OPENTABLE)
@ContextConfiguration(classes = EmbeddedPostgresConfiguration.class)
public class EmbeddedPostgresIntegrationTest {
    // class body...
}
```

### Using Yandex Provider

Before you use Yandex provider, you have to add the following Maven dependency:

```xml
<dependency>
    <groupId>ru.yandex.qatools.embed</groupId>
    <artifactId>postgresql-embedded</artifactId>
    <version>2.10</version>
    <scope>test</scope>
</dependency>
```

Then, you can use `@AutoConfigureEmbeddedDatabase` annotation to set up the `DatabaseProvider.YANDEX` provider.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(provider = YANDEX)
public class YandexProviderIntegrationTest {
    // class body...
}
```

#### Yandex-provider specific configuration

The provider configuration can be managed via properties in the `zonky.test.database.postgres.yandex-provider` group.

```properties
zonky.test.database.postgres.yandex-provider.postgres-version=11.10-1 # Version of EnterpriseDB PostgreSQL binaries (https://www.enterprisedb.com/download-postgresql-binaries).
```

## Advanced Topics

### Refresh Mode

This feature allows for reset the database to the initial state that existed before the test was being started.
It's based on the use of template databases and does not rely on the rollback of the current transaction.
So it's possible to save and commit data within the test without any consequences.

Due to the use of the template databases, the refresh operation can be relatively fast and efficient.
If database prefetching is used and tuned properly, the refresh operation can take only a few milliseconds.

Note that the refresh mode can be combined with `@FlywayTest` annotation without any negative impact on performance.

Example of using the refresh mode: [Refreshing the database during tests](#refreshing-the-database-during-tests)

### Database Prefetching

The database prefetching feature is used to speed up the initialization and refreshing of the embedded databases. It can be customized by properties in the `zonky.test.database.prefetching` group.

```properties
zonky.test.database.prefetching.thread-name-prefix=prefetching- # Prefix to use for the names of database prefetching threads.
zonky.test.database.prefetching.concurrency=3                   # Maximum number of concurrently running database prefetching threads.
zonky.test.database.prefetching.pipeline-cache-size=5           # Maximum number of prepared databases per pipeline.
zonky.test.database.prefetching.max-prepared-templates=10       # Maximum number of prepared database templates.
```

### Disabling auto-configuration

By default, the library automatically registers all necessary context customizers and test execution listeners.
If this behavior is inappropriate for some reason, you can deactivate it by exclusion of the `embedded-database-spring-test-autoconfigure` dependency.

```xml
<dependency>
    <groupId>io.zonky.test</groupId>
    <artifactId>embedded-database-spring-test</artifactId>
    <version>2.1.2</version>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>io.zonky.test</groupId>
            <artifactId>embedded-database-spring-test-autoconfigure</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### Background bootstrapping mode

This feature is enabled out of the box and causes that the initialization of the data source and the execution of Flyway or Liquibase migrations are performed in background bootstrap mode.
In such case, a `DataSource` proxy is immediately returned for injection purposes instead of waiting for the bootstrapping to complete.
However, note that the first actual call to a data source method will then block until the bootstrapping completed, if not ready by then.
For maximum benefit, make sure to avoid early data source calls in init methods of related beans.

## Troubleshooting

### Connecting to the embedded database

When you use a breakpoint to pause the test, you can connect to the temporary embedded database. Connection details can be found in the log as shown in the example below:

```log
i.z.t.d.l.EmbeddedDatabaseReporter - JDBC URL to connect to 'dataSource1': url='jdbc:postgresql://localhost:55112/fynwkrpzfcyj?user=postgres', scope='TestClass#testMethod'
```

If you are using [refresh mode](#refresh-mode) or the `@FlywayTest` annotation, there may be several similar records in the log but always with a different scope. That's because in such case multiple isolated databases may be created.

### Process [/tmp/embedded-pg/PG-XYZ/bin/initdb, ...] failed

Check the console output for an `initdb: cannot be run as root` message. If the error is present, try to upgrade to a newer version of the library (1.5.5+), or ensure the build process to be running as a non-root user.

If the error is not present, try to clean up the `/tmp/embedded-pg/PG-XYZ` directory containing temporary binaries of the embedded database.

### Running tests on Windows does not work

You probably need to install [Microsoft Visual C++ 2013 Redistributable Package](https://support.microsoft.com/en-us/help/3179560/update-for-visual-c-2013-and-visual-c-redistributable-package). The version 2013 is important, installation of other versions does not help. More detailed is the problem discussed [here](https://github.com/opentable/otj-pg-embedded/issues/65).

### Running tests in Docker does not work

Running builds inside a Docker container is fully supported, including Alpine Linux. However, PostgreSQL has a restriction the database process must run under a non-root user. Otherwise, the database does not start and fails with an error.  

So be sure to use a docker image that uses a non-root user. Or, since version `1.5.5` you can run the docker container with `--privileged` option, which allows taking advantage of `unshare` command to run the database process in a separate namespace.

Below are some examples of how to prepare a docker image running with a non-root user:

<details>
  <summary>Standard Dockerfile</summary>
  
  ```dockerfile
  FROM openjdk:8-jdk
  
  RUN groupadd --system --gid 1000 test
  RUN useradd --system --gid test --uid 1000 --shell /bin/bash --create-home test
  
  USER test
  WORKDIR /home/test
  ```

</details>

<details>
  <summary>Alpine Dockerfile</summary>
  
  ```dockerfile
  FROM openjdk:8-jdk-alpine
  
  RUN addgroup -S -g 1000 test
  RUN adduser -D -S -G test -u 1000 -s /bin/ash test
  
  USER test
  WORKDIR /home/test
  ```

</details>

<details>
  <summary>Gitlab runner Docker executor</summary>

  Configure Docker container to run in privileged mode as described [here](https://docs.gitlab.com/runner/executors/docker.html#use-docker-in-docker-with-privileged-mode).

  ```
  [[runners]]
    executor = "docker"
    [runners.docker]
      privileged = true
  ```

</details>

### Using Docker provider inside a Docker container

This combination is supported, however, additional configuration is required.
You have to add `-v /var/run/docker.sock:/var/run/docker.sock` mapping to the Docker command to map the Docker socket.
Detailed instructions are [here](https://www.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/).

### Frequent and repeated initialization of the database

Make sure you do not use `org.flywaydb.test.junit.FlywayTestExecutionListener` or `org.flywaydb.test.FlywayTestExecutionListener` listener. Because this library has its own test execution listener that can optimize database initialization.
But this optimization has no effect if any of these listeners is also applied.

## Building from Source
The project uses a [Gradle](http://gradle.org)-based build system. In the instructions
below, [`./gradlew`](http://vimeo.com/34436402) is invoked from the root of the source tree and serves as
a cross-platform, self-contained bootstrap mechanism for the build.

### Prerequisites

[Git](http://help.github.com/set-up-git-redirect) and [JDK 8 or later](http://www.oracle.com/technetwork/java/javase/downloads)

Be sure that your `JAVA_HOME` environment variable points to the `jdk1.8.0` folder
extracted from the JDK download.

### Check out sources
`git clone git@github.com:zonkyio/embedded-database-spring-test.git`

### Compile and test
`./gradlew build`

## Project dependencies

* [Spring Framework](http://www.springsource.org/) (4.3.25) - `spring-test`, `spring-context` modules
* [Testcontainers](https://www.testcontainers.org) (1.15.0)
* [Cedarsoftware](https://github.com/jdereg/java-util) (1.34.0)
* [Guava](https://github.com/google/guava) (23.0)

## License
The project is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0.html).
