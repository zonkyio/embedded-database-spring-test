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

package io.zonky.test.db.postgres;

import io.zonky.test.db.logging.EmbeddedDatabaseReporter;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.GenericDatabaseProvider;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

/**
 * Implementation of the {@link org.springframework.beans.factory.FactoryBean} interface
 * that provides empty instances of the embedded postgres database.
 */
public class EmptyEmbeddedPostgresDataSourceFactoryBean implements FactoryBean<DataSource>, InitializingBean {

    protected final DatabaseDescriptor databaseDescriptor;

    @Autowired
    protected GenericDatabaseProvider databaseProvider;

    private DataSource dataSource;

    public EmptyEmbeddedPostgresDataSourceFactoryBean(DatabaseDescriptor databaseDescriptor) {
        this.databaseDescriptor = databaseDescriptor;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Class<?> getObjectType() {
        return DataSource.class;
    }

    @Override
    public DataSource getObject() {
        return dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        dataSource = databaseProvider.getDatabase(EmptyDatabasePreparer.INSTANCE, databaseDescriptor);
        EmbeddedDatabaseReporter.reportDataSource(dataSource);
    }

    private static class EmptyDatabasePreparer implements DatabasePreparer {

        public static final EmptyDatabasePreparer INSTANCE = new EmptyDatabasePreparer();

        private EmptyDatabasePreparer() {
            // direct initialization is forbidden
        }

        @Override
        public void prepare(DataSource dataSource) {
            // nothing to do
        }
    }
}
