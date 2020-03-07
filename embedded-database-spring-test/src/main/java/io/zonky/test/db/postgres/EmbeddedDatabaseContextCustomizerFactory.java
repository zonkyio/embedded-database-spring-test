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

import com.google.common.collect.ImmutableMap;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.Replace;
import io.zonky.test.db.AutoConfigureEmbeddedDatabases;
import io.zonky.test.db.flyway.DefaultDataSourceContext;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.config.EmbeddedDatabaseConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.DEFAULT;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;

/**
 * Implementation of the {@link org.springframework.test.context.ContextCustomizerFactory} interface,
 * which is responsible for initialization of the embedded postgres database and its registration to the application context.
 * The applied initialization strategy is driven by the {@link AutoConfigureEmbeddedDatabase} annotation.
 *
 * @see AutoConfigureEmbeddedDatabase
 */
public class EmbeddedDatabaseContextCustomizerFactory implements ContextCustomizerFactory {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedDatabaseContextCustomizerFactory.class);

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        Set<AutoConfigureEmbeddedDatabase> databaseAnnotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(
                testClass, AutoConfigureEmbeddedDatabase.class, AutoConfigureEmbeddedDatabases.class);

        databaseAnnotations = databaseAnnotations.stream()
                .filter(distinctByKey(AutoConfigureEmbeddedDatabase::beanName))
                .filter(databaseAnnotation -> databaseAnnotation.type() == DatabaseType.POSTGRES)
                .filter(databaseAnnotation -> databaseAnnotation.replace() != Replace.NONE)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!databaseAnnotations.isEmpty()) {
            return new EmbeddedDatabaseContextCustomizer(databaseAnnotations);
        }

        return null;
    }

    protected static class EmbeddedDatabaseContextCustomizer implements ContextCustomizer {

        private final Set<AutoConfigureEmbeddedDatabase> databaseAnnotations;

        public EmbeddedDatabaseContextCustomizer(Set<AutoConfigureEmbeddedDatabase> databaseAnnotations) {
            this.databaseAnnotations = databaseAnnotations;
        }

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            context.addBeanFactoryPostProcessor(new EnvironmentPostProcessor(context.getEnvironment()));

            BeanDefinitionRegistry registry = getBeanDefinitionRegistry(context);

            RootBeanDefinition configDefinition = new RootBeanDefinition(EmbeddedDatabaseConfiguration.class);
            registry.registerBeanDefinition("embeddedDatabaseConfiguration", configDefinition);

            RootBeanDefinition registrarDefinition = new RootBeanDefinition(EmbeddedDatabaseRegistrar.class);
            registrarDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, databaseAnnotations);
            registry.registerBeanDefinition("embeddedDatabaseRegistrar", registrarDefinition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EmbeddedDatabaseContextCustomizer that =
                    (EmbeddedDatabaseContextCustomizer) o;

            return databaseAnnotations.equals(that.databaseAnnotations);
        }

        @Override
        public int hashCode() {
            return databaseAnnotations.hashCode();
        }
    }

    protected static class EnvironmentPostProcessor implements BeanDefinitionRegistryPostProcessor {

        private final ConfigurableEnvironment environment;

        public EnvironmentPostProcessor(ConfigurableEnvironment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    EmbeddedDatabaseContextCustomizer.class.getSimpleName(),
                    ImmutableMap.of("spring.test.database.replace", "NONE")));
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // nothing to do
        }
    }

    protected static class EmbeddedDatabaseRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

        private final Set<AutoConfigureEmbeddedDatabase> databaseAnnotations;

        private Environment environment;

        public EmbeddedDatabaseRegistrar(Set<AutoConfigureEmbeddedDatabase> databaseAnnotations) {
            this.databaseAnnotations = databaseAnnotations;
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            Assert.isInstanceOf(ConfigurableListableBeanFactory.class, registry,
                    "Embedded Database Auto-configuration can only be used with a ConfigurableListableBeanFactory");
            ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) registry;

            for (AutoConfigureEmbeddedDatabase databaseAnnotation : databaseAnnotations) {
                DatabaseDescriptor databaseDescriptor = resolveDatabaseDescriptor(environment, databaseAnnotation);
                BeanDefinitionHolder dataSourceInfo = getDataSourceBeanDefinition(beanFactory, databaseAnnotation);

                String dataSourceBeanName = dataSourceInfo.getBeanName();
                String dataSourceContextBeanName = dataSourceBeanName + "Context";

                RootBeanDefinition dataSourceContextDefinition = new RootBeanDefinition(DefaultDataSourceContext.class);
                dataSourceContextDefinition.getPropertyValues().addPropertyValue("descriptor", databaseDescriptor);

                RootBeanDefinition dataSourceDefinition = new RootBeanDefinition();
                dataSourceDefinition.setPrimary(dataSourceInfo.getBeanDefinition().isPrimary());
                dataSourceDefinition.setBeanClass(EmbeddedPostgresDataSourceFactoryBean.class);
                dataSourceDefinition.getConstructorArgumentValues()
                        .addIndexedArgumentValue(0, dataSourceContextBeanName);

                if (registry.containsBeanDefinition(dataSourceBeanName)) {
                    logger.info("Replacing '{}' DataSource bean with embedded version", dataSourceBeanName);
                    registry.removeBeanDefinition(dataSourceBeanName);
                }

                registry.registerBeanDefinition(dataSourceContextBeanName, dataSourceContextDefinition);
                registry.registerBeanDefinition(dataSourceBeanName, dataSourceDefinition);
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // nothing to do
        }

        protected DatabaseDescriptor resolveDatabaseDescriptor(Environment environment, AutoConfigureEmbeddedDatabase databaseAnnotation) {
            checkState(databaseAnnotation.provider() == DEFAULT || databaseAnnotation.providerName().isEmpty(),
                    "Set either AutoConfigureEmbeddedDatabase#provider or AutoConfigureEmbeddedDatabase#providerName, but not both");
            String providerName =
                    databaseAnnotation.provider() != DEFAULT ? databaseAnnotation.provider().name() :
                            !databaseAnnotation.providerName().isEmpty() ? databaseAnnotation.providerName() :
                                    environment.getProperty("zonky.test.database.provider", "docker");
            String databaseName = databaseAnnotation.type().name();
            return new DatabaseDescriptor(databaseName, providerName);
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
            return new BeanDefinitionHolder(new RootBeanDefinition(), "dataSource");
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

    protected static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}
