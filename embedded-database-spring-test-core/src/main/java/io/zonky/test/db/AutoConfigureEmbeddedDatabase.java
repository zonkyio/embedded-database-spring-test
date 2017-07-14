/*
 * Copyright 2016 the original author or authors.
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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to a test class to configure a embedded test database to use
 * instead of any application defined {@link DataSource}.
 * </p>
 * This annotation is handled and processed by {@link io.zonky.test.db.postgres.EmbeddedPostgresContextCustomizerFactory}.
 *
 * @see io.zonky.test.db.postgres.EmbeddedPostgresContextCustomizerFactory
 * @see io.zonky.test.db.flyway.OptimizedFlywayTestExecutionListener
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoConfigureEmbeddedDatabase {

    /**
     * If this attribute is set then a new data source
     * with the specified name is created instead of overriding an existing one.
     *
     * @return the name of a new data source bean to be created
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
    EmbeddedDatabaseType type() default EmbeddedDatabaseType.POSTGRES;

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
     * A supported embedded database type.
     */
    enum EmbeddedDatabaseType {

        /**
         * The Embedded PostgreSQL Database
         */
        POSTGRES

    }
}
