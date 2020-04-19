# <img src="zonky.jpg" height="100"> Embedded Database

## Introduction

The primary goal of this project is to make it easier to write Spring-powered integration tests that rely on PostgreSQL database. This library is responsible for creating and managing isolated embedded databases for each test class or test method, based on test configuration.

## Features

* Supports both `Spring` and `Spring Boot` frameworks
    * Supported versions are Spring 4.3.8+ and Spring Boot 1.4.6+
* Automatic integration with Spring TestContext framework
    * Context caching is fully supported
* Seamless integration with Flyway database migration tool
    * Just place `@FlywayTest` annotation on test class or method
* Optimized initialization and cleaning of embedded databases
    * Database templates are used to reduce the loading time
* Uses lightweight bundles of [PostgreSQL binaries](https://github.com/zonkyio/embedded-postgres-binaries) with reduced size
    * Providing configurable version of PostgreSQL binaries
    * Providing PostgreSQL 11+ binaries even for Linux platform
* Support for running inside Docker, including Alpine Linux

## Quick Start

### Maven Configuration

Add the following Maven dependency:

```xml
<dependency>
    <groupId>io.zonky.test</groupId>
    <artifactId>embedded-database-spring-test</artifactId>
    <version>1.5.3</version>
    <scope>test</scope>
</dependency>
```

The default version of the embedded database is `PostgreSQL 10.11`, but you can change it by following the instructions described in [Changing the version of postgres binaries](#changing-the-version-of-postgres-binaries).

### Basic Usage

The configuration of the embedded database is driven by `@AutoConfigureEmbeddedDatabase` annotation. Just place the annotation on a test class and that's it! The existing data source will be replaced by the testing one, or a new data source will be created.

## Examples

### Creating a new empty database

A new data source will be created and injected into all related components. You can also inject it into test class as shown below. 

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

In case the test class uses a spring context that already contains a data source bean, the data source bean will be automatically replaced by a testing data source. Please note that if the context contains multiple data sources the bean name must be specified by `@AutoConfigureEmbeddedDatabase(beanName = "dataSource")` to identify the data source that will be replaced. The newly created data source bean will be injected into all related components and you can also inject it into test class.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("path/to/application-config.xml")
public class EmptyDatabaseIntegrationTest {
    // class body...
}
```

### Creating multiple databases within a single test class

The `@AutoConfigureEmbeddedDatabase` is a repeatable annotation, so you can annotate a test class with multiple annotations to create multiple independent databases.
Each of them can have completely different configuration parameters, including the database provider as demonstrated in the example below.

Note that if multiple annotations on a single class are applied, some optimization techniques can not be used and database initialization may be slower.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource1")
@AutoConfigureEmbeddedDatabase(beanName = "dataSource2")
@AutoConfigureEmbeddedDatabase(beanName = "dataSource3", provider = DOCKER)
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

### Using `@FlywayTest` annotation on a test class

The library supports the use of `@FlywayTest` annotation. If you use it, the embedded database will be automatically initialized and cleaned by Flyway database migration tool. If you don't specify any custom migration locations the default path `db/migration` will be applied.

Note that if you place the annotation on a class, all tests within the class share the same database. If you want all the tests to be isolated, you need to put the `@FlywayTest` annotation on each test method separately.

```java
@RunWith(SpringRunner.class)
@FlywayTest
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("path/to/application-config.xml")
public class FlywayMigrationIntegrationTest {
    // class body...
}
```

### Using `@FlywayTest` annotation on a test method

It is also possible to use `@FlywayTest` annotation on a test method. In such case, the isolated embedded database will be created and managed for the duration of the test method. If you don't specify any custom migration locations the default path `db/migration` will be applied.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("path/to/application-config.xml")
public class FlywayMigrationIntegrationTest {
    
    @Test
    @FlywayTest(locationsForMigrate = "test/db/migration")
    public void testMethod() {
        // method body...
    }
}
```

### Using `@FlywayTest` annotation with additional options

In case you want to apply migrations from some additional locations, you can use `@FlywayTest(locationsForMigrate = "path/to/migrations")` configuration. In that case, the sql scripts from the default location and also sql scripts from the additional locations will be applied. If you need to prevent the loading of the scripts from the default location you can use `@FlywayTest(overrideLocations = true, ...)` annotation configuration.

See [Usage of Annotation FlywayTest](https://github.com/flyway/flyway-test-extensions/wiki/Usage-of-Annotation-FlywayTest) for more information about configuration options of `@FlywayTest` annotation.

```java
@RunWith(SpringRunner.class)
@FlywayTest(locationsForMigrate = "test/db/migration")
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("path/to/application-config.xml")
public class FlywayMigrationIntegrationTest {
    // class body...
}
```

### Using `@DataJpaTest` or `@JdbcTest` annotation

Spring Boot provides several annotations to simplify writing integration tests.
One of them is the `@DataJpaTest` annotation, which can be used when a test focuses only on JPA components.
By default, tests annotated with this annotation use an in-memory database.
But if the `@DataJpaTest` annotation is used together with the `@AutoConfigureEmbeddedDatabase` annotation,
the in-memory database is automatically disabled and replaced by the embedded postgres database. 

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
  @AutoConfigureEmbeddedDatabase
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

## Advanced Topics

### Database Providers

The library can be combined with different database providers.
Each of them has its advantages and disadvantages summarized in the table below.

Docker provides the greatest flexibility, but it can be slightly slower than the native versions.
However, the change of database providers is really easy, so you can try them all.

You can either configure a provider for each class separately by `@AutoConfigureEmbeddedDatabase(provider = ...)` annotation,
or through `zonky.test.database.provider` property globally.

|                                   |       [Docker][docker-provider]      |             [Zonky][zonky-provider]             | [OpenTable][opentable-provider] | [Yandex][yandex-provider] |
|:---------------------------------:|:------------------------------------:|:-----------------------------------------------:|:-------------------------------:|:-------------------------:|
|          **Startup Time**         |            Slightly slower           |                       Fast                      |               Fast              | Slow, depends on platform |
|          **Performance**          | Slightly slower, depends on platform |                      Native                     |              Native             |           Native          |
|      **Supported Platforms**      |        All supported by Docker       |       Mac OS, Windows, Linux, Alpine Linux      |      Mac OS, Windows, Linux     |   Mac OS, Windows, Linux  |
|    **Supported Architectures**    |            Based on image            | amd64, i386, arm32v6, arm32v7, arm64v8, ppc64le |              amd64              |           amd64           |
| **Configurable Postgres Version** |            Yes, at runtime           |               Yes, at compile time              |                No               |      Yes, at runtime      |
|      **Alpine Linux Support**     |                  Yes                 |                       Yes                       |                No               |             No            |
|       **Extension Support**       |                  Yes                 |                        No                       |                No               |             No            |
|       **In-Memory Support**       |                  Yes                 |                        No                       |                No               |             No            |

[docker-provider]: https://www.testcontainers.org
[zonky-provider]: https://github.com/zonkyio/embedded-postgres
[opentable-provider]: https://github.com/opentable/otj-pg-embedded
[yandex-provider]: https://github.com/yandex-qatools/postgresql-embedded

### Common Configuration

The `@AutoConfigureEmbeddedDatabase` annotation can be used for some basic configuration, advanced configuration requires properties or yaml files.
The following configuration keys are honored by all providers:

```properties
zonky.test.database.provider=zonky # Provider used to create the underlying embedded database, see the documentation for the comparision matrix.
zonky.test.database.postgres.client.properties.*= # Additional properties used to configure the test data source.
zonky.test.database.postgres.initdb.properties.*= # Additional properties to pass to initdb command during the database initialization.
zonky.test.database.postgres.server.properties.*= # Additional properties used to configure the embedded PostgreSQL server.
```

Note that the library includes [configuration metadata](embedded-database-spring-test/src/main/resources/META-INF/spring-configuration-metadata.json) that offer contextual help and code completion as users are working with Spring Boot's `application.properties` or `application.yml` files.

**Example configuration:**
```properties
zonky.test.database.postgres.client.properties.stringtype=unspecified
zonky.test.database.postgres.initdb.properties.lc-collate=cs_CZ.UTF-8
zonky.test.database.postgres.initdb.properties.lc-monetary=cs_CZ.UTF-8
zonky.test.database.postgres.initdb.properties.lc-numeric=cs_CZ.UTF-8
zonky.test.database.postgres.server.properties.shared_buffers=512MB
zonky.test.database.postgres.server.properties.max_connections=100
```

### Using Zonky Provider (default)

This is the default provider, so you do not have to do anything special,
just use the `@AutoConfigureEmbeddedDatabase` annotation in its basic form without any provider.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
public class DefaultProviderIntegrationTest {
    // class body...
}
```

#### Changing the version of postgres binaries [![zonky-provider only](https://img.shields.io/badge/-zonky--provider%20only-3399ff.svg)](#database-providers)

The version of the binaries can be managed by importing `embedded-postgres-binaries-bom` in a required version into your dependency management section.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.zonky.test.postgres</groupId>
            <artifactId>embedded-postgres-binaries-bom</artifactId>
            <version>11.6.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

<details>
  <summary>Using Maven BOMs in Gradle</summary>
  
  In Gradle, there are several ways how to import a Maven BOM.
  
  1. You can define a resolution strategy to check and change the version of transitive dependencies manually:
  
         configurations.all {
              resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                  if (details.requested.group == 'io.zonky.test.postgres') {
                     details.useVersion '11.6.0'
                 }
             }
         }
  
  2. If you use Gradle 5+, [Maven BOMs are supported out of the box](https://docs.gradle.org/current/userguide/managing_transitive_dependencies.html#sec:bom_import), so you can import the bom:
  
         dependencies {
              implementation enforcedPlatform('io.zonky.test.postgres:embedded-postgres-binaries-bom:11.6.0')
         }
  
  3. Or, you can use [Spring's dependency management plugin](https://docs.spring.io/dependency-management-plugin/docs/current-SNAPSHOT/reference/html5/#dependency-management-configuration-bom-import) that provides Maven-like dependency management to Gradle:
  
         plugins {
             id "io.spring.dependency-management" version "1.0.6.RELEASE"
         }
         
         dependencyManagement {
              imports {
                   mavenBom 'io.zonky.test.postgres:embedded-postgres-binaries-bom:11.6.0'
              }
         }

</details><br/>

A list of all available versions of postgres binaries is here: https://mvnrepository.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-bom

Note that the release cycle of the postgres binaries is independent of the release cycle of this library, so you can upgrade to a new version of postgres binaries immediately after it is released.

#### Enabling support for additional architectures [![zonky-provider only](https://img.shields.io/badge/-zonky--provider%20only-3399ff.svg)](#database-providers)

By default, only the support for `amd64` architecture is enabled.
Support for other architectures can be enabled by adding the corresponding Maven dependencies as shown in the example below.

```xml
<dependency>
    <groupId>io.zonky.test.postgres</groupId>
    <artifactId>embedded-postgres-binaries-linux-i386</artifactId>
    <scope>test</scope>
</dependency>
```

**Supported platforms:** `Darwin`, `Windows`, `Linux`, `Alpine Linux`  
**Supported architectures:** `amd64`, `i386`, `arm32v6`, `arm32v7`, `arm64v8`, `ppc64le`

Note that not all architectures are supported by all platforms, look here for an exhaustive list of all available artifacts: https://mvnrepository.com/artifact/io.zonky.test.postgres
  
Since `PostgreSQL 10.0`, there are additional artifacts with `alpine-lite` suffix. These artifacts contain postgres binaries for Alpine Linux with disabled [ICU support](https://blog.2ndquadrant.com/icu-support-postgresql-10/) for further size reduction.

#### Zonky-provider specific configuration [![zonky-provider only](https://img.shields.io/badge/-zonky--provider%20only-3399ff.svg)](#database-providers)

The provider configuration can be customized with bean implementing `Consumer<EmbeddedPostgres.Builder>` interface.
The obtained builder provides methods to change the configuration before the database is started.

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
@AutoConfigureEmbeddedDatabase
@ContextConfiguration(classes = EmbeddedPostgresConfiguration.class)
public class EmbeddedPostgresIntegrationTest {
    // class body...
}
```

### Using Docker Provider

Docker provider is especially useful if you need some PostgreSQL extension.
You can use any docker image that is compatible with the [official Postgres image](https://hub.docker.com/_/postgres).

Before you use Docker provider, you must add the following Maven dependency:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.10.6</version>
    <scope>test</scope>
</dependency>
```

Then, you can use `@AutoConfigureEmbeddedDatabase` annotation to set up the `DatabaseProvider.DOCKER` provider.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(provider = DOCKER)
public class DockerProviderIntegrationTest {
    // class body...
}
```

#### Docker-provider specific configuration

The provider configuration can be controlled by properties in the `zonky.test.database.postgres.docker` group.

```properties
zonky.test.database.postgres.docker.image=postgres:10.11-alpine # Docker image containing PostgreSQL database.
zonky.test.database.postgres.docker.tmpfs.enabled=false # Whether to mount postgres data directory as tmpfs.
zonky.test.database.postgres.docker.tmpfs.options=rw,noexec,nosuid # Mount options used to configure the tmpfs filesystem.
``` 

Or, the provider configuration can be also customized with bean implementing `PostgreSQLContainerCustomizer` interface.

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
@AutoConfigureEmbeddedDatabase(provider = DOCKER)
@ContextConfiguration(classes = EmbeddedPostgresConfiguration.class)
public class EmbeddedPostgresIntegrationTest {
    // class body...
}
```

### Using OpenTable Provider

Before you use OpenTable provider, you must add the following Maven dependency:

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
The obtained builder provides methods to change the configuration before the database is started.

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

Before you use Yandex provider, you must add the following Maven dependency:

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

The provider configuration can be controlled by properties in the `zonky.test.database.postgres.yandex-provider` group.

```properties
zonky.test.database.postgres.yandex-provider.postgres-version=10.11-1 # Version of EnterpriseDB PostgreSQL binaries (https://www.enterprisedb.com/download-postgresql-binaries).
```

### Database Prefetching

Database prefetching is used to speed up the database initialization. It can be customized by properties in the `zonky.test.database.prefetching` group.

```properties
zonky.test.database.prefetching.thread-name-prefix=prefetching- # Prefix to use for the names of database prefetching threads.
zonky.test.database.prefetching.concurrency=3 # Maximum number of concurrently running database prefetching threads.
zonky.test.database.prefetching.pipeline-cache-size=3 # Maximum number of prepared databases per pipeline.
```

### Disabling auto-configuration

By default, the library automatically registers all necessary context customizers and test execution listeners.
If this behavior is inappropriate for some reason, you can deactivate it by exclusion of the `embedded-database-spring-test-autoconfigure` dependency.

```xml
<dependency>
    <groupId>io.zonky.test</groupId>
    <artifactId>embedded-database-spring-test</artifactId>
    <version>1.5.3</version>
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

Using this feature causes that the initialization of the data source and the execution of Flyway database migrations are performed in background bootstrap mode.
In such case, a `DataSource` proxy is immediately returned for injection purposes instead of waiting for the Flyway's bootstrapping to complete.
However, note that the first actual call to a data source method will then block until the Flyway's bootstrapping completed, if not ready by then.
For maximum benefit, make sure to avoid early data source calls in init methods of related beans.

```java
@Configuration
public class BootstrappingConfiguration {
    
    @Bean
    public FlywayDataSourceContext flywayDataSourceContext(TaskExecutor bootstrapExecutor) {
        DefaultFlywayDataSourceContext dataSourceContext = new DefaultFlywayDataSourceContext();
        dataSourceContext.setBootstrapExecutor(bootstrapExecutor);
        return dataSourceContext;
    }

    @Bean
    public TaskExecutor bootstrapExecutor() {
        return new SimpleAsyncTaskExecutor("bootstrapExecutor-");
    }
}
```

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ContextConfiguration(classes = BootstrappingConfiguration.class)
public class FlywayMigrationIntegrationTest {
    // class body...
}
```

## Troubleshooting

### Connecting to embedded database

When you use a breakpoint to pause the tests, you can connect to a temporary embedded database. Connection details can be found in the log as shown in the example below:

```log
i.z.t.d.l.EmbeddedDatabaseReporter - JDBC URL to connect to 'dataSource1': url='jdbc:postgresql://localhost:55112/fynwkrpzfcyj?user=postgres', scope='TestClass#testMethod'
```

If you are using `@FlywayTest` annotation, there may be several similar records in the log but always with a different scope. That's because in such case multiple isolated databases may be created.

### Process [/tmp/embedded-pg/PG-XYZ/bin/initdb, ...] failed

Try to remove `/tmp/embedded-pg/PG-XYZ` directory containing temporary binaries of the embedded postgres database. That should solve the problem.

### Running tests on Windows does not work

You probably need to install the [Microsoft Visual C++ 2013 Redistributable Package](https://support.microsoft.com/en-us/help/3179560/update-for-visual-c-2013-and-visual-c-redistributable-package). The version 2013 is important, installation of other versions does not help. More detailed is the problem discussed [here](https://github.com/opentable/otj-pg-embedded/issues/65).

### Running tests inside Docker does not work

Running build inside Docker is fully supported, including Alpine Linux. But you must keep in mind that the **PostgreSQL database must be run under a non-root user**. Otherwise, the database does not start and fails with an error.

So be sure to use a docker image that uses a non-root user, or you can use any of the following Dockerfiles to prepare your own image.

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

### Using Docker provider inside a Docker container

This combination is supported, however, additional configuration is required.
You need to add `-v /var/run/docker.sock:/var/run/docker.sock` mapping to the Docker command to map the Docker socket.
Detailed instructions are [here](https://www.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/).

### Frequent and repeated initialization of the database

Make sure that you do not use `org.flywaydb.test.junit.FlywayTestExecutionListener`. Because this library has its own test execution listener that can optimize database initialization.
But this optimization has no effect if `FlywayTestExecutionListener` is also applied.

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

* [PostgreSQL Binaries](https://github.com/zonkyio/embedded-postgres-binaries) (10.11)
* [Embedded Postgres](https://github.com/zonkyio/embedded-postgres) (1.2.6) - a fork of [OpenTable Embedded PostgreSQL Component](https://github.com/opentable/otj-pg-embedded)
* [Spring Framework](http://www.springsource.org/) (4.3.22) - `spring-test`, `spring-context` modules
* [Flyway](https://github.com/flyway/) (5.0.7)
* [Guava](https://github.com/google/guava) (23.0)

## License
The project is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0.html).
