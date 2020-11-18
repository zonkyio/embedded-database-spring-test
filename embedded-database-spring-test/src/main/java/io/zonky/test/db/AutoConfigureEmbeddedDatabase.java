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

package io.zonky.test.db;

import javax.sql.DataSource;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to a test class to configure an embedded database to use
 * instead of any application defined {@link DataSource}.
 * <p>
 * This annotation may be used as a <em>meta-annotation</em> to create custom <em>composed annotations</em>.
 *
 * @see EmbeddedDatabaseContextCustomizerFactory
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(AutoConfigureEmbeddedDatabases.class)
public @interface AutoConfigureEmbeddedDatabase {

    /**
     * The bean name to identify the data source to be associated with the embedded database.
     *
     * <p>It is required only if the application context contains multiple DataSource beans.
     *
     * @return the bean name to identify the DataSource bean
     */
    String beanName() default "";

    /**
     * Determines the refresh mode of the embedded database.
     *
     * <p>This feature allows for reset the database to its initial state that existed before the test began.
     * It is based on the use of template databases and does not rely on the rollback of the current transaction,
     * so it is possible to save and commit data within the test without any issues.
     *
     * <p>The refresh mode may also be configured via the {@code zonky.test.database.refresh} configuration property.
     *
     * @return the type of refresh mode to be used for database reset
     */
    RefreshMode refresh() default RefreshMode.NEVER;

    /**
     * The type of embedded database to be created when {@link #replace() replacing} the data source.
     * By default will attempt to detect the database type based on the classpath.
     *
     * <p>The database type may also be configured via the {@code zonky.test.database.type} configuration property.
     *
     * @return the type of embedded database
     */
    DatabaseType type() default DatabaseType.AUTO;

    /**
     * Provider to be used to create the underlying embedded database, see the documentation for the comparison matrix.
     *
     * <p>The provider may also be configured via the {@code zonky.test.database.provider} configuration property.
     *
     * @return the provider to create the embedded database
     */
    DatabaseProvider provider() default DatabaseProvider.DEFAULT;

    /**
     * Determines what type of existing DataSource beans can be replaced.
     *
     * <p>It may also be configured via the {@code zonky.test.database.replace} configuration property.
     *
     * @return the type of existing DataSource to replace
     */
    Replace replace() default Replace.ANY;

    /**
     * The supported refresh modes.
     */
    enum RefreshMode {

        /**
         * The database will not be reset at all.
         */
        NEVER,

        /**
         * The database will be reset to its initial state before the test class.
         */
        BEFORE_CLASS,

        /**
         * The database will be reset to its initial state before each test method in the class.
         */
        BEFORE_EACH_TEST_METHOD,

        /**
         * The database will be reset to its initial state after each test method in the class.
         */
        AFTER_EACH_TEST_METHOD,

        /**
         * The database will be reset to its initial state after the test class.
         */
        AFTER_CLASS

    }

    /**
     * The supported types of embedded databases.
     */
    enum DatabaseType {

        /**
         * Database is detected automatically based on the classpath.
         */
        AUTO,

        /**
         * PostgreSQL Database
         */
        POSTGRES,

        /**
         * Microsoft SQL Server
         */
        MSSQL,

        /**
         * MySQL Database
         */
        MYSQL,

        /**
         * MariaDB Database
         */
        MARIADB

    }

    /**
     * The supported providers for creating embedded databases.
     */
    enum DatabaseProvider {

        /**
         * Default typically equals to {@link #DOCKER} provider,
         * unless a different default has been configured by configuration properties.
         */
        DEFAULT,

        /**
         * Run the embedded database in Docker as a container.
         */
        DOCKER,

        /**
         * Use Zonky's fork of OpenTable Embedded PostgreSQL Component to create the embedded database
         * (<a href="https://github.com/zonkyio/embedded-postgres">https://github.com/zonkyio/embedded-postgres</a>).
         */
        ZONKY,

        /**
         * Use OpenTable Embedded PostgreSQL Component to create the embedded database
         * (<a href="https://github.com/opentable/otj-pg-embedded">https://github.com/opentable/otj-pg-embedded</a>).
         */
        OPENTABLE,

        /**
         * Use Yandex's Embedded PostgreSQL Server to create the embedded database
         * (<a href="https://github.com/yandex-qatools/postgresql-embedded">https://github.com/yandex-qatools/postgresql-embedded</a>).
         */
        YANDEX,

    }

    /**
     * What the test database can replace.
     */
    enum Replace {

        /**
         * Replace any DataSource bean (auto-configured or manually defined).
         */
        ANY,

        /**
         * Don't replace the application default DataSource.
         */
        NONE

    }
}
