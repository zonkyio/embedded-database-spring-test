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
     * The bean name to be used to identify the data source that will be replaced.
     * It is only necessary if there is no existing DataSource
     * or the context contains multiple DataSource beans.
     *
     * @return the name to identify the DataSource bean
     */
    String beanName() default "";

    /**
     * Determines what type of existing DataSource beans can be replaced.
     *
     * @return the type of existing DataSource to replace
     */
    Replace replace() default Replace.ANY;

    /**
     * The type of an embedded database to be initialized
     * when {@link #replace() replacing} the data source.
     *
     * @return the type of an embedded database
     */
    DatabaseType type() default DatabaseType.POSTGRES;

    /**
     * Provider used to create the underlying embedded database,
     * see the documentation for the comparision matrix.
     * Note that the provider can also be configured
     * through {@code zonky.test.database.provider} property.
     *
     * @return the provider to create the embedded database
     */
    DatabaseProvider provider() default DatabaseProvider.DEFAULT;

    // TODO: update javadoc
    String providerName() default "";

    // TODO: update javadoc
    RefreshMode refreshMode() default RefreshMode.NEVER;

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

    /**
     * The supported types of embedded databases.
     */
    enum DatabaseType {

        /**
         * PostgreSQL Database
         */
        POSTGRES,

        // TODO: update javadoc
        MSSQL,

        // TODO: update javadoc
        MYSQL

    }

    /**
     * The supported providers of embedded databases.
     */
    enum DatabaseProvider {

        /**
         * Default typically equals to {@link #DOCKER} provider,
         * unless a different default has been configured by externalized configuration.
         */
        DEFAULT,

        /**
         * Run the embedded database in a Docker container.
         */
        DOCKER,

        /**
         * Use Zonky's fork of OpenTable Embedded PostgreSQL Component to create the embedded database (https://github.com/zonkyio/embedded-postgres).
         */
        ZONKY,

        /**
         * Use OpenTable Embedded PostgreSQL Component to create the embedded database (https://github.com/opentable/otj-pg-embedded).
         */
        OPENTABLE,

        /**
         * Use Yandex's Embedded PostgreSQL Server to create the embedded database (https://github.com/yandex-qatools/postgresql-embedded).
         */
        YANDEX,

    }

    enum RefreshMode {

        NEVER,

        BEFORE_CLASS,

        BEFORE_EACH_TEST_METHOD,

        AFTER_EACH_TEST_METHOD,

        AFTER_CLASS

    }
}
