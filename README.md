## Introduction

The primary goal of this project is to make it easier to write Spring-powered integration tests that rely on PostgreSQL database. This library is responsible for creating and managing isolated embedded databases for each test class or test method, based on test configuration.

## Features

* Integration with Spring TestContext Framework
* Both `Spring` and `Spring Boot` Frameworks are supported
* Lightweight bundle of PostgreSQL database with reduced size
* Integration with Flyway database migration tool
* Optimized migration and cleaning of embedded database
    * Database templates are used to reduce the loading time

## Quick Start

### Maven Configuration

Add the Maven dependency:

```xml
<dependency>
  <groupId>io.zonky.test</groupId>
  <artifactId>embedded-database-spring-test</artifactId>
  <version>1.0.0</version>
  <scope>test</scope>
</dependency>
```

### Basic Usage

The configuration of the embedded database is driven by `@AutoConfigureEmbeddedDatabase` annotation. Just place the annotation on a test class and that's it! The existing data source will be replaced by the testing one, or a new data source will be created.

### Examples

#### Creating a new empty database with a specified bean name

A new data source with a specified name will be created and injected into all related components. You can also inject it into test class as shown below. 

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
public class EmptyDatabaseIntegrationTest {
    
    @Autowired
    private DataSource dataSource;
    
    // class body...
}
```

#### Replacing an existing data source with an empty database

In case the test class uses a context configuration that already contains a data source, it will be automatically replaced by the testing data source. Please note that if the context contains multiple data sources the bean name must be specified by `@AutoConfigureEmbeddedDatabase(beanName = "dataSource")` to identify the data source that will be replaced. The newly created data source bean will be injected into all related components and you can also inject it into test class.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("/path/to/app-config.xml")
public class EmptyDatabaseIntegrationTest {
    // class body...
}
```

#### Using `@FlywayTest` annotation on a test class

The library supports the use of `@FlywayTest` annotation. When you use it, the embedded database will automatically be initialized by Flyway database migration tool. If you don't specify any custom migration locations the default path `db/migration` will be applied.

```java
@RunWith(SpringRunner.class)
@FlywayTest
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("/path/to/app-config.xml")
public class FlywayMigrationIntegrationTest {
    // class body...
}
```

#### Using `@FlywayTest` annotation with additional options

In case you want to apply migrations from some additional locations, you can use `@FlywayTest(locationsForMigrate = "path/to/migrations")` configuration. In this case, the sql scripts from the default location and also sql scripts from the additional locations will be applied. If you need to prevent the loading of the scripts from the default location you can use `@FlywayTest(overrideLocations = true, ...)` annotation configuration.

See [Usage of Annotation FlywayTest](https://github.com/flyway/flyway-test-extensions/wiki/Usage-of-Annotation-FlywayTest) for more information about configuration options of `@FlywayTest` annotation.

```java
@RunWith(SpringRunner.class)
@FlywayTest(locationsForMigrate = "test/db/migration")
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("/path/to/app-config.xml")
public class FlywayMigrationIntegrationTest {
    // class body...
}
```

#### Using `@FlywayTest` annotation on a test method

It is also possible to use `@FlywayTest` annotation on a test method. In such case, the isolated embedded database will be created and managed for the duration of the test method. If you don't specify any custom migration locations the default path `db/migration` will be applied.

```java
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ContextConfiguration("/path/to/app-config.xml")
public class FlywayMigrationIntegrationTest {
    
    @Test
    @FlywayTest(locationsForMigrate = "test/db/migration")
    public void testMethod() {
        // method body...
    }
}
```

#### Background bootstrapping mode

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
@FlywayTest
@AutoConfigureEmbeddedDatabase
@ContextConfiguration(classes = BootstrappingConfiguration.class)
public class FlywayMigrationIntegrationTest {
    // class body...
}
```

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

## Supported databases

* [PostgreSQL](https://www.postgresql.org/) (9.6.3)

## Project dependencies

* [Spring Framework](http://www.springsource.org/) (4.3.10) - `spring-test`, `spring-context` modules
* [OpenTable Embedded PostgreSQL Component](https://github.com/opentable/otj-pg-embedded) (0.9.0)
* [Flyway](https://github.com/flyway/) (4.2.0)
* [Guava](https://github.com/google/guava) (23.0)

## License
The project is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0.html).
