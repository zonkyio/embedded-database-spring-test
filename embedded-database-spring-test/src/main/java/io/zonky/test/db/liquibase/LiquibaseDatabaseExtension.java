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

package io.zonky.test.db.liquibase;

import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.util.AopProxyUtils;
import liquibase.integration.spring.SpringLiquibase;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.NameMatchMethodPointcutAdvisor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

import static com.google.common.base.Preconditions.checkState;

public class LiquibaseDatabaseExtension implements BeanPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof AopInfrastructureBean) {
            return bean;
        }

        if (bean instanceof SpringLiquibase) {
            SpringLiquibase liquibase = (SpringLiquibase) bean;
            DatabaseContext context = AopProxyUtils.getDatabaseContext(liquibase.getDataSource());

            if (context != null) {
                if (bean instanceof Advised && !((Advised) bean).isFrozen()) {
                    ((Advised) bean).addAdvisor(0, createAdvisor(liquibase));
                    return bean;
                } else {
                    ProxyFactory proxyFactory = new ProxyFactory(bean);
                    proxyFactory.addAdvisor(createAdvisor(liquibase));
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

    protected Advisor createAdvisor(SpringLiquibase liquibase) {
        Advice advice = new LiquibaseDatabaseExtensionInterceptor(liquibase);
        NameMatchMethodPointcutAdvisor advisor = new NameMatchMethodPointcutAdvisor(advice);
        advisor.setMappedNames("afterPropertiesSet");
        return advisor;
    }

    protected static class LiquibaseDatabaseExtensionInterceptor implements MethodInterceptor {

        protected final SpringLiquibase liquibase;

        protected LiquibaseDatabaseExtensionInterceptor(SpringLiquibase liquibase) {
            this.liquibase = liquibase;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            if (!"afterPropertiesSet".equals(invocation.getMethod().getName())) {
                return invocation.proceed();
            }

            LiquibaseDescriptor descriptor = LiquibaseDescriptor.from(liquibase);
            LiquibaseDatabasePreparer preparer = new LiquibaseDatabasePreparer(descriptor);

            DatabaseContext context = AopProxyUtils.getDatabaseContext(liquibase.getDataSource());
            checkState(context != null, "Data source context cannot be resolved");
            context.apply(preparer);

            return null;
        }
    }
}
