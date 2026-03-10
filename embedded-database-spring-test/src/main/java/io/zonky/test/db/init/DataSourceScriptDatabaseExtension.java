/*
 * Copyright 2025 the original author or authors.
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

package io.zonky.test.db.init;

import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.util.AopProxyUtils;
import io.zonky.test.db.util.ReflectionUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

public class DataSourceScriptDatabaseExtension implements BeanPostProcessor, Ordered {

    private final boolean enabled;

    public DataSourceScriptDatabaseExtension(Environment environment) {
        this.enabled = environment.getProperty("zonky.test.database.spring.optimized-sql-init.enabled", boolean.class, true);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (enabled && bean instanceof DataSourceScriptDatabaseInitializer) {
            DataSourceScriptDatabaseInitializer initializer = (DataSourceScriptDatabaseInitializer) bean;
            DataSource dataSource = ReflectionUtils.getField(initializer, "dataSource");
            DatabaseContext context = AopProxyUtils.getDatabaseContext(dataSource);

            if (context != null) {
                context.apply(new DataSourceScriptDatabasePreparer(initializer));
                return new SuppressedInitializerWrapper(initializer);
            }
        }

        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }

    public static class SuppressedInitializerWrapper {

        private final DataSourceScriptDatabaseInitializer initializer;

        public SuppressedInitializerWrapper(DataSourceScriptDatabaseInitializer initializer) {
            this.initializer = initializer;
        }

        @Override
        public String toString() {
            return "SuppressedInitializerWrapper{initializer=" + initializer.getClass().getName() + "}";
        }
    }
}
