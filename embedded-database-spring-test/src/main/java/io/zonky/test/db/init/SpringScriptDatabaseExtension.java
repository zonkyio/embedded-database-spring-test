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
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.NameMatchMethodPointcutAdvisor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

public class SpringScriptDatabaseExtension implements BeanPostProcessor, Ordered {

    private final boolean enabled;

    public SpringScriptDatabaseExtension(Environment environment) {
        this.enabled = environment.getProperty("zonky.test.database.spring.optimized-sql-init.enabled", boolean.class, true);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (!enabled || bean instanceof AopInfrastructureBean) {
            return bean;
        }

        if (bean instanceof DataSourceScriptDatabaseInitializer) {
            DataSourceScriptDatabaseInitializer initializer = (DataSourceScriptDatabaseInitializer) bean;
            DataSource dataSource = ReflectionUtils.getField(initializer, "dataSource");
            DatabaseContext context = AopProxyUtils.getDatabaseContext(dataSource);

            if (context != null) {
                if (bean instanceof Advised && !((Advised) bean).isFrozen()) {
                    ((Advised) bean).addAdvisor(0, createAdvisor(initializer, context));
                    return bean;
                } else {
                    ProxyFactory proxyFactory = new ProxyFactory(bean);
                    proxyFactory.addAdvisor(createAdvisor(initializer, context));
                    proxyFactory.setProxyTargetClass(true);
                    return proxyFactory.getProxy();
                }
            }
        }

        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }

    protected Advisor createAdvisor(DataSourceScriptDatabaseInitializer initializer, DatabaseContext context) {
        Advice advice = new SpringScriptDatabaseExtensionInterceptor(initializer, context);
        NameMatchMethodPointcutAdvisor advisor = new NameMatchMethodPointcutAdvisor(advice);
        advisor.setMappedNames("afterPropertiesSet", "initializeDatabase");
        return advisor;
    }

    protected static class SpringScriptDatabaseExtensionInterceptor implements MethodInterceptor {

        private final SpringScriptDatabasePreparer preparer;
        private final DatabaseContext context;

        protected SpringScriptDatabaseExtensionInterceptor(DataSourceScriptDatabaseInitializer initializer,
                                                           DatabaseContext context) {
            this.preparer = new SpringScriptDatabasePreparer(initializer);
            this.context = context;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            switch (invocation.getMethod().getName()) {
                case "afterPropertiesSet":
                    context.apply(preparer);
                    return null;
                case "initializeDatabase":
                    context.apply(preparer);
                    return true;
                default:
                    return invocation.proceed();
            }
        }
    }
}
