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

package io.zonky.test.db;

import com.google.common.collect.ImmutableMap;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.Replace;
import io.zonky.test.db.config.EmbeddedDatabaseConfiguration;
import io.zonky.test.db.config.EmbeddedDatabaseFactoryBean;
import io.zonky.test.db.context.DatabaseDescriptor;
import io.zonky.test.db.context.DefaultDataSourceContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkState;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.DEFAULT;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import static java.util.stream.Collectors.toCollection;

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

        Set<DatabaseDefinition> databaseDefinitions = databaseAnnotations.stream()
                .filter(distinctByKey(AutoConfigureEmbeddedDatabase::beanName))
                .filter(annotation -> annotation.type() == DatabaseType.POSTGRES)
                .filter(annotation -> annotation.replace() != Replace.NONE)
                .map(this::createDatabaseDefinition)
                .collect(toCollection(LinkedHashSet::new));

        if (!databaseDefinitions.isEmpty()) {
            return new EmbeddedDatabaseContextCustomizer(databaseDefinitions);
        }

        return null;
    }

    protected DatabaseDefinition createDatabaseDefinition(AutoConfigureEmbeddedDatabase annotation) {
        checkState(annotation.provider() == DEFAULT || annotation.providerName().isEmpty(),
                "Set either AutoConfigureEmbeddedDatabase#provider or AutoConfigureEmbeddedDatabase#providerName, but not both");

        String providerName = annotation.provider() != DEFAULT ?
                annotation.provider().name() : annotation.providerName();

        return new DatabaseDefinition(annotation.beanName(), annotation.type(), providerName);
    }

    protected static class EmbeddedDatabaseContextCustomizer implements ContextCustomizer {

        private final Set<DatabaseDefinition> databaseDefinitions;

        public EmbeddedDatabaseContextCustomizer(Set<DatabaseDefinition> databaseDefinitions) {
            this.databaseDefinitions = databaseDefinitions;
        }

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            context.addBeanFactoryPostProcessor(new EnvironmentPostProcessor(context.getEnvironment()));

            BeanDefinitionRegistry registry = getBeanDefinitionRegistry(context);

            RootBeanDefinition configDefinition = new RootBeanDefinition(EmbeddedDatabaseConfiguration.class);
            registry.registerBeanDefinition("embeddedDatabaseConfiguration", configDefinition);

            RootBeanDefinition registrarDefinition = new RootBeanDefinition(EmbeddedDatabaseRegistrar.class);
            registrarDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, databaseDefinitions);
            registry.registerBeanDefinition("embeddedDatabaseRegistrar", registrarDefinition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EmbeddedDatabaseContextCustomizer that =
                    (EmbeddedDatabaseContextCustomizer) o;

            return databaseDefinitions.equals(that.databaseDefinitions);
        }

        @Override
        public int hashCode() {
            return databaseDefinitions.hashCode();
        }
    }

    protected static class DatabaseDefinition {

        private final String beanName;
        private final DatabaseType databaseType;
        private final String providerName;

        public DatabaseDefinition(String beanName, DatabaseType databaseType, String providerName) {
            this.beanName = beanName;
            this.databaseType = databaseType;
            this.providerName = providerName;
        }

        public String getBeanName() {
            return beanName;
        }

        public DatabaseType getDatabaseType() {
            return databaseType;
        }

        public String getProviderName() {
            return providerName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DatabaseDefinition that = (DatabaseDefinition) o;
            return Objects.equals(beanName, that.beanName) &&
                    databaseType == that.databaseType &&
                    Objects.equals(providerName, that.providerName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(beanName, databaseType, providerName);
        }
    }

    protected static class EnvironmentPostProcessor implements BeanDefinitionRegistryPostProcessor {

        private final ConfigurableEnvironment environment;

        public EnvironmentPostProcessor(ConfigurableEnvironment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    EmbeddedDatabaseContextCustomizer.class.getSimpleName(),
                    ImmutableMap.of("spring.test.database.replace", "AUTO_CONFIGURED")));
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            // nothing to do
        }
    }

    protected static class EmbeddedDatabaseRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

        private final Set<DatabaseDefinition> databaseDefinitions;

        private Environment environment;

        public EmbeddedDatabaseRegistrar(Set<DatabaseDefinition> databaseDefinitions) {
            this.databaseDefinitions = databaseDefinitions;
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            Assert.isInstanceOf(ConfigurableListableBeanFactory.class, registry,
                    "Embedded Database Auto-configuration can only be used with a ConfigurableListableBeanFactory");
            ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) registry;

            for (DatabaseDefinition databaseDefinition : databaseDefinitions) {
                DatabaseDescriptor databaseDescriptor = resolveDatabaseDescriptor(environment, databaseDefinition);
                BeanDefinitionHolder dataSourceInfo = getDataSourceBeanDefinition(beanFactory, databaseDefinition);

                String dataSourceBeanName = dataSourceInfo.getBeanName();
                String dataSourceContextBeanName = dataSourceBeanName + "Context";

                RootBeanDefinition dataSourceContextDefinition = new RootBeanDefinition(DefaultDataSourceContext.class);
                dataSourceContextDefinition.getPropertyValues().addPropertyValue("descriptor", databaseDescriptor);

                RootBeanDefinition dataSourceDefinition = new RootBeanDefinition();
                dataSourceDefinition.setPrimary(dataSourceInfo.getBeanDefinition().isPrimary());
                dataSourceDefinition.setBeanClass(EmbeddedDatabaseFactoryBean.class);
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
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            // nothing to do
        }

        protected DatabaseDescriptor resolveDatabaseDescriptor(Environment environment, DatabaseDefinition databaseDefinition) {
            String providerName = !databaseDefinition.getProviderName().isEmpty() ? databaseDefinition.getProviderName() :
                    environment.getProperty("zonky.test.database.provider", "docker");
            String databaseName = databaseDefinition.getDatabaseType().name();
            return DatabaseDescriptor.of(databaseName, providerName);
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

    protected static BeanDefinitionHolder getDataSourceBeanDefinition(ConfigurableListableBeanFactory beanFactory, DatabaseDefinition databaseDefinition) {
        if (StringUtils.isNotBlank(databaseDefinition.getBeanName())) {
            if (beanFactory.containsBean(databaseDefinition.getBeanName())) {
                BeanDefinition beanDefinition = beanFactory.getBeanDefinition(databaseDefinition.getBeanName());
                return new BeanDefinitionHolder(beanDefinition, databaseDefinition.getBeanName());
            } else {
                return new BeanDefinitionHolder(new RootBeanDefinition(), databaseDefinition.getBeanName());
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
