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
import io.zonky.test.db.config.EmbeddedDatabaseAutoConfiguration;
import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.context.DefaultDatabaseContext;
import io.zonky.test.db.context.EmbeddedDatabaseFactoryBean;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.support.DatabaseDefinition;
import io.zonky.test.db.support.DatabaseProviders;
import io.zonky.test.db.support.ProviderDescriptor;
import io.zonky.test.db.support.ProviderResolver;
import io.zonky.test.db.util.AnnotationUtils;
import io.zonky.test.db.util.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.AnnotationMetadata;
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

import static java.util.stream.Collectors.toCollection;

/**
 * Implementation of the {@link org.springframework.test.context.ContextCustomizerFactory} interface,
 * which is responsible for initialization of the embedded database and its registration to the application context.
 * The applied initialization strategy is driven by the {@link AutoConfigureEmbeddedDatabase} annotation.
 *
 * @see AutoConfigureEmbeddedDatabase
 */
public class EmbeddedDatabaseContextCustomizerFactory implements ContextCustomizerFactory {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedDatabaseContextCustomizerFactory.class);

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        Set<AutoConfigureEmbeddedDatabase> annotations = AnnotationUtils.getDatabaseAnnotations(testClass);

        if (!annotations.isEmpty()) {
            Set<DatabaseDefinition> definitions = annotations.stream()
                    .filter(annotation -> annotation.replace() != Replace.NONE)
                    .map(a -> new DatabaseDefinition(a.beanName(), a.type(), a.provider()))
                    .collect(toCollection(LinkedHashSet::new));

            return new EmbeddedDatabaseContextCustomizer(definitions);
        }

        return null;
    }

    protected static class EmbeddedDatabaseContextCustomizer implements ContextCustomizer {

        private final Set<DatabaseDefinition> databaseDefinitions;

        public EmbeddedDatabaseContextCustomizer(Set<DatabaseDefinition> databaseDefinitions) {
            this.databaseDefinitions = databaseDefinitions;
        }

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            context.addBeanFactoryPostProcessor(new EnvironmentPostProcessor(databaseDefinitions, context.getEnvironment()));

            BeanDefinitionRegistry registry = getBeanDefinitionRegistry(context);

            RootBeanDefinition configDefinition = new RootBeanDefinition(EmbeddedDatabaseConfiguration.class);
            registry.registerBeanDefinition(EmbeddedDatabaseConfiguration.BEAN_NAME, configDefinition);

            RootBeanDefinition registrarDefinition = new RootBeanDefinition(EmbeddedDatabaseRegistrar.class);
            registrarDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, databaseDefinitions);
            registry.registerBeanDefinition(EmbeddedDatabaseRegistrar.BEAN_NAME, registrarDefinition);
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

    protected static class EnvironmentPostProcessor implements BeanDefinitionRegistryPostProcessor {

        private final Set<DatabaseDefinition> databaseDefinitions;
        private final ConfigurableEnvironment environment;

        public EnvironmentPostProcessor(Set<DatabaseDefinition> databaseDefinitions, ConfigurableEnvironment environment) {
            this.databaseDefinitions = databaseDefinitions;
            this.environment = environment;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            Replace zonkyDatabaseReplace = getDatabaseReplaceMode(environment, databaseDefinitions);
            String springDatabaseReplace = zonkyDatabaseReplace == Replace.NONE ? "NONE" : "AUTO_CONFIGURED";

            environment.getPropertySources().addFirst(new MapPropertySource(
                    EmbeddedDatabaseContextCustomizer.class.getSimpleName(),
                    ImmutableMap.of("spring.test.database.replace", springDatabaseReplace)));
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            // nothing to do
        }
    }

    @Configuration
    @Import(EmbeddedDatabaseConfiguration.Selector.class)
    protected static class EmbeddedDatabaseConfiguration {

        protected static final String BEAN_NAME = EmbeddedDatabaseConfiguration.class.getName();

        protected static class Selector implements DeferredImportSelector, Ordered {

            @Override
            public String[] selectImports(AnnotationMetadata importingClassMetadata) {
                return new String[] { EmbeddedDatabaseAutoConfiguration.class.getName() };
            }

            @Override
            public int getOrder() {
                return Ordered.LOWEST_PRECEDENCE;
            }
        }
    }

    protected static class EmbeddedDatabaseRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

        protected static final String BEAN_NAME = EmbeddedDatabaseRegistrar.class.getName();

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

            Replace replace = getDatabaseReplaceMode(environment, databaseDefinitions);
            if (replace == Replace.NONE) {
                logger.info("The use of the embedded database has been disabled");
                return;
            }

            for (DatabaseDefinition databaseDefinition : databaseDefinitions) {
                BeanDefinitionHolder dataSourceInfo = getDataSourceBeanDefinition(beanFactory, databaseDefinition);

                String dataSourceBeanName = dataSourceInfo.getBeanName();
                String contextBeanName = dataSourceBeanName + "Context";

                RootBeanDefinition contextDefinition = new RootBeanDefinition();
                contextDefinition.setBeanClass(DefaultDatabaseContext.class);
                contextDefinition.setPrimary(dataSourceInfo.getBeanDefinition().isPrimary());
                contextDefinition.getConstructorArgumentValues()
                        .addIndexedArgumentValue(0, (ObjectFactory<DatabaseProvider>) () -> {
                            ProviderResolver providerResolver = beanFactory.getBean(ProviderResolver.class);
                            DatabaseProviders databaseProviders = beanFactory.getBean(DatabaseProviders.class);
                            ProviderDescriptor providerDescriptor = providerResolver.getDescriptor(databaseDefinition);
                            return databaseProviders.getProvider(providerDescriptor);
                        });

                RootBeanDefinition dataSourceDefinition = new RootBeanDefinition();
                dataSourceDefinition.setBeanClass(EmbeddedDatabaseFactoryBean.class);
                dataSourceDefinition.setPrimary(dataSourceInfo.getBeanDefinition().isPrimary());
                dataSourceDefinition.getConstructorArgumentValues()
                        .addIndexedArgumentValue(0, (ObjectFactory<DatabaseContext>) () ->
                                beanFactory.getBean(contextBeanName, DatabaseContext.class));

                logger.info("Replacing '{}' DataSource bean with embedded version", dataSourceBeanName);

                if (registry.containsBeanDefinition(dataSourceBeanName)) {
                    registry.removeBeanDefinition(dataSourceBeanName);
                }

                registry.registerBeanDefinition(contextBeanName, contextDefinition);
                registry.registerBeanDefinition(dataSourceBeanName, dataSourceDefinition);
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            // nothing to do
        }
    }

    protected static Replace getDatabaseReplaceMode(Environment environment, Set<DatabaseDefinition> databaseDefinitions) {
        return databaseDefinitions.isEmpty() ? Replace.NONE :
                PropertyUtils.getEnumProperty(environment, "zonky.test.database.replace", Replace.class, Replace.ANY);
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
        if (StringUtils.hasText(databaseDefinition.getBeanName())) {
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
}
