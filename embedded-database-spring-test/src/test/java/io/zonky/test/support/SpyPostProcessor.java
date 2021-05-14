package io.zonky.test.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.function.BiPredicate;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public class SpyPostProcessor implements BeanPostProcessor {

    private final BiPredicate<Object, String> predicate;

    public SpyPostProcessor(BiPredicate<Object, String> predicate) {
        this.predicate = predicate;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (predicate.test(bean, beanName)) {
            return mock(bean.getClass(), withSettings()
                    .defaultAnswer(CALLS_REAL_METHODS)
                    .spiedInstance(bean));
        }
        return bean;
    }
}
