package io.zonky.test.db.config;

import org.junit.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EmbeddedDatabaseAutoConfigurationTest {

    // Non-static @Bean methods returning a BeanPostProcessor force the configuration class to be
    // instantiated early, which makes Spring log "not eligible for getting processed by all BeanPostProcessors".
    @Test
    public void beanPostProcessorFactoryMethodsAreStatic() {
        List<String> nonStatic = new ArrayList<>();
        for (Method method : EmbeddedDatabaseAutoConfiguration.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)
                    && BeanPostProcessor.class.isAssignableFrom(method.getReturnType())
                    && !Modifier.isStatic(method.getModifiers())) {
                nonStatic.add(method.getName());
            }
        }
        assertThat(nonStatic).isEmpty();
    }
}
