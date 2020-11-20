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

package io.zonky.test.db.context;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.Ordered;

import javax.sql.DataSource;

/**
 * Implementation of the {@link org.springframework.beans.factory.FactoryBean} interface
 * that provides fully cacheable instances of the embedded postgres database.
 */
// TODO: replace by using factory method (java configuration)
public class EmbeddedDatabaseFactoryBean implements FactoryBean<DataSource>, Ordered {

    private final ObjectFactory<DatabaseContext> databaseContextProvider;

    private DataSource dataSource;

    public EmbeddedDatabaseFactoryBean(ObjectFactory<DatabaseContext> databaseContextProvider) {
        this.databaseContextProvider = databaseContextProvider;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
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
    public synchronized DataSource getObject() {
        if (dataSource == null) {
            DatabaseContext databaseContext = databaseContextProvider.getObject();
            dataSource = ProxyFactory.getProxy(DataSource.class, new DatabaseTargetSource(databaseContext));
        }
        return dataSource;
    }
}
