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

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.core.Ordered;

import javax.sql.DataSource;

import io.zonky.test.db.flyway.DataSourceContext;

/**
 * Implementation of the {@link org.springframework.beans.factory.FactoryBean} interface
 * that provides fully cacheable instances of the embedded postgres database.
 */
// TODO: replace by using factory method (java configuration)
public class EmbeddedPostgresDataSourceFactoryBean implements FactoryBean<DataSource>, BeanFactoryAware, Ordered {

    private final String dataSourceContextName;

    private BeanFactory beanFactory;
    private DataSource proxyInstance;

    public EmbeddedPostgresDataSourceFactoryBean(String dataSourceContextName) {
        this.dataSourceContextName = dataSourceContextName;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
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
        if (proxyInstance == null) {
            DataSourceContext dataSourceContext = beanFactory.getBean(dataSourceContextName, DataSourceContext.class);
            proxyInstance = ProxyFactory.getProxy(DataSource.class, dataSourceContext);
        }
        return proxyInstance;
    }
}
