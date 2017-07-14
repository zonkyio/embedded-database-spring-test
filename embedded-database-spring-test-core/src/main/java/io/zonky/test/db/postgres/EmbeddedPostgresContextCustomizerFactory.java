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

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.EmbeddedDatabaseType;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.Replace;
import io.zonky.test.db.flyway.FlywayDataSourceContext;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.ObjectUtils;

import javax.sql.DataSource;
import java.util.List;

/**
 * Implementation of the {@link org.springframework.test.context.ContextCustomizerFactory} interface,
 * which is responsible for initialization of the embedded postgres database and its registration to the application context.
 * The applied initialization strategy is driven by the {@link AutoConfigureEmbeddedDatabase} annotation.
 *
 * @see AutoConfigureEmbeddedDatabase
 */
public class EmbeddedPostgresContextCustomizerFactory implements ContextCustomizerFactory {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedPostgresContextCustomizerFactory.class);

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        AutoConfigureEmbeddedDatabase databaseAnnotation = AnnotatedElementUtils.findMergedAnnotation(testClass, AutoConfigureEmbeddedDatabase.class);

        if (databaseAnnotation != null
                && databaseAnnotation.type() == EmbeddedDatabaseType.POSTGRES
                && databaseAnnotation.replace() != Replace.NONE) {
            return new PreloadableEmbeddedPostgresContextCustomizer(databaseAnnotation);
        }

        return null;
    }

    private static class PreloadableEmbeddedPostgresContextCustomizer implements ContextCustomizer {

        private final AutoConfigureEmbeddedDatabase databaseAnnotation;

        private PreloadableEmbeddedPostgresContextCustomizer(AutoConfigureEmbeddedDatabase databaseAnnotation) {
            this.databaseAnnotation = databaseAnnotation;
        }

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            Class<?> testClass = mergedConfig.getTestClass();
            FlywayTest flywayAnnotation = AnnotatedElementUtils.findMergedAnnotation(testClass, FlywayTest.class);

            BeanDefinitionRegistry registry = getBeanDefinitionRegistry(context);
            RootBeanDefinition registrarDefinition = new RootBeanDefinition();

            registrarDefinition.setBeanClass(PreloadableEmbeddedPostgresRegistrar.class);
            registrarDefinition.getConstructorArgumentValues()
                    .addIndexedArgumentValue(0, databaseAnnotation);
            registrarDefinition.getConstructorArgumentValues()
                    .addIndexedArgumentValue(1, flywayAnnotation);

            registry.registerBeanDefinition("preloadableEmbeddedPostgresRegistrar", registrarDefinition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PreloadableEmbeddedPostgresContextCustomizer that =
                    (PreloadableEmbeddedPostgresContextCustomizer) o;

            return databaseAnnotation.equals(that.databaseAnnotation);
        }

        @Override
        public int hashCode() {
            return databaseAnnotation.hashCode();
        }
    }

    private static class PreloadableEmbeddedPostgresRegistrar implements BeanDefinitionRegistryPostProcessor {

        private final AutoConfigureEmbeddedDatabase databaseAnnotation;
        private final FlywayTest flywayAnnotation;

        private PreloadableEmbeddedPostgresRegistrar(AutoConfigureEmbeddedDatabase databaseAnnotation, FlywayTest flywayAnnotation) {
            this.databaseAnnotation = databaseAnnotation;
            this.flywayAnnotation = flywayAnnotation;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) registry;

            BeanDefinitionHolder dataSourceInfo = getDataSourceBeanDefinition(beanFactory, databaseAnnotation);

            RootBeanDefinition dataSourceDefinition = new RootBeanDefinition();
            dataSourceDefinition.setPrimary(dataSourceInfo.getBeanDefinition().isPrimary());

            BeanDefinitionHolder flywayInfo = getFlywayBeanDefinition(beanFactory, flywayAnnotation);
            if (flywayInfo == null) {
                dataSourceDefinition.setBeanClass(EmptyEmbeddedPostgresDataSourceFactoryBean.class);
            } else {
                String contextBeanName = flywayInfo.getBeanName() + "DataSourceContext";
                RootBeanDefinition dataSourceContextDefinition = new RootBeanDefinition();
                dataSourceContextDefinition.setBeanClass(FlywayDataSourceContext.class);
                registry.registerBeanDefinition(contextBeanName, dataSourceContextDefinition);

                dataSourceDefinition.setBeanClass(FlywayEmbeddedPostgresDataSourceFactoryBean.class);

                dataSourceDefinition.getConstructorArgumentValues()
                        .addIndexedArgumentValue(0, flywayInfo.getBeanName());
                dataSourceDefinition.getConstructorArgumentValues()
                        .addIndexedArgumentValue(1, new RuntimeBeanReference(contextBeanName));
            }

            logger.info("Replacing '{}' DataSource bean with embedded version", dataSourceInfo.getBeanName());
            registry.registerBeanDefinition(dataSourceInfo.getBeanName(), dataSourceDefinition);
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // nothing to do
        }
    }

    protected static BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
        if (context instanceof BeanDefinitionRegistry) {
            return (BeanDefinitionRegistry) context;
        }
        if (context instanceof AbstractApplicationContext) {
            return (BeanDefinitionRegistry) ((AbstractApplicationContext) context).getBeanFactory();
        }
        throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
    }

    protected static BeanDefinitionHolder getDataSourceBeanDefinition(ConfigurableListableBeanFactory beanFactory, AutoConfigureEmbeddedDatabase annotation) {
        if (StringUtils.isNotBlank(annotation.beanName())) {
            if (beanFactory.containsBean(annotation.beanName())) {
                BeanDefinition beanDefinition = beanFactory.getBeanDefinition(annotation.beanName());
                return new BeanDefinitionHolder(beanDefinition, annotation.beanName());
            } else {
                return new BeanDefinitionHolder(new RootBeanDefinition(), annotation.beanName());
            }
        }

        String[] beanNames = beanFactory.getBeanNamesForType(DataSource.class);

        if (ObjectUtils.isEmpty(beanNames)) {
            throw new IllegalStateException("No DataSource beans found, embedded version will not be used, " +
                    "you must specify data source name - use @AutoConfigureEmbeddedDatabase(beanName = \"dataSource\") annotation");
        }

        if (beanNames.length == 1) {
            String beanName = beanNames[0];
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            return new BeanDefinitionHolder(beanDefinition, beanName);
        }

        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition.isPrimary()) {
                return new BeanDefinitionHolder(beanDefinition, beanName);
            }
        }

        throw new IllegalStateException("No primary DataSource found, embedded version will not be used");
    }

    protected static BeanDefinitionHolder getFlywayBeanDefinition(ConfigurableListableBeanFactory beanFactory, FlywayTest annotation) {
        if (annotation != null && StringUtils.isNotBlank(annotation.flywayName())) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(annotation.flywayName());
            return new BeanDefinitionHolder(beanDefinition, annotation.flywayName());
        }

        String[] beanNames = beanFactory.getBeanNamesForType(Flyway.class);

        if (ObjectUtils.isEmpty(beanNames)) {
            return null;
        }

        if (beanNames.length == 1) {
            String beanName = beanNames[0];
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            return new BeanDefinitionHolder(beanDefinition, beanName);
        }

        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition.isPrimary()) {
                return new BeanDefinitionHolder(beanDefinition, beanName);
            }
        }

        return null;
    }
}
