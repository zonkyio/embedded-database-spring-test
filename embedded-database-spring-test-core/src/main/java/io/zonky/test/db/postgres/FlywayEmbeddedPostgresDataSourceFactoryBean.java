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

import com.google.common.base.Throwables;
import io.zonky.test.db.flyway.FlywayDataSourceContext;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

import javax.sql.DataSource;

/**
 * Implementation of the {@link org.springframework.beans.factory.FactoryBean} interface
 * that provides fully cacheable instances of the embedded postgres database.
 * Each instance is backed by a flyway bean that is used for initializing the target database.
 */
public class FlywayEmbeddedPostgresDataSourceFactoryBean implements FactoryBean<DataSource>, BeanPostProcessor, InitializingBean, Ordered {

    private final String flywayName;
    private final FlywayDataSourceContext dataSourceContext;

    private DataSource proxyInstance;

    public FlywayEmbeddedPostgresDataSourceFactoryBean(String flywayName, FlywayDataSourceContext dataSourceContext) {
        this.flywayName = flywayName;
        this.dataSourceContext = dataSourceContext;
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
    public DataSource getObject() throws Exception {
        return proxyInstance;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        proxyInstance = ProxyFactory.getProxy(DataSource.class, dataSourceContext);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Flyway && StringUtils.equals(beanName, flywayName)) {
            try {
                dataSourceContext.reload((Flyway) bean);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}
