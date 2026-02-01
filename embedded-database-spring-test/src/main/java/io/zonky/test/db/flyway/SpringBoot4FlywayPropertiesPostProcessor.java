package io.zonky.test.db.flyway;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.core.Ordered;

public class SpringBoot4FlywayPropertiesPostProcessor implements BeanPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof FlywayProperties) {
            FlywayProperties properties = (FlywayProperties) bean;
            properties.setUrl(null);
            properties.setUser(null);
            properties.setPassword(null);
        }
        return bean;
    }
}
