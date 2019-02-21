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
import io.zonky.test.db.flyway.DefaultFlywayDataSourceContext;
import io.zonky.test.db.flyway.FlywayClassUtils;
import io.zonky.test.db.flyway.FlywayDataSourceContext;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.ProviderType;
import io.zonky.test.db.provider.impl.DockerPostgresDatabaseProvider;
import io.zonky.test.db.provider.impl.OpenTablePostgresDatabaseProvider;
import io.zonky.test.db.provider.impl.PrefetchingDatabaseProvider;
import io.zonky.test.db.provider.impl.YandexPostgresDatabaseProvider;
import io.zonky.test.db.provider.impl.ZonkyPostgresDatabaseProvider;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
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
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.DEFAULT;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;

/**
 * Implementation of the {@link org.springframework.test.context.ContextCustomizerFactory} interface,
 * which is responsible for initialization of the embedded postgres database and its registration to the application context.
 * The applied initialization strategy is driven by the {@link AutoConfigureEmbeddedDatabase} annotation.
 *
 * @see AutoConfigureEmbeddedDatabase
 */
public class EmbeddedPostgresContextCustomizerFactory implements ContextCustomizerFactory {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedPostgresContextCustomizerFactory.class);

    private static final boolean flywayNameAttributePresent = FlywayClassUtils.isFlywayNameAttributePresent();
    private static final boolean repeatableAnnotationPresent = FlywayClassUtils.isRepeatableFlywayTestAnnotationPresent();

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
            return new PreloadableEmbeddedPostgresContextCustomizer(databaseAnnotations);
        }

        return null;
    }

    protected static class PreloadableEmbeddedPostgresContextCustomizer implements ContextCustomizer {

        private final Set<AutoConfigureEmbeddedDatabase> databaseAnnotations;

        public PreloadableEmbeddedPostgresContextCustomizer(Set<AutoConfigureEmbeddedDatabase> databaseAnnotations) {
            this.databaseAnnotations = databaseAnnotations;
        }

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            context.addBeanFactoryPostProcessor(new EnvironmentPostProcessor(context.getEnvironment()));

            BeanDefinitionRegistry registry = getBeanDefinitionRegistry(context);
            RootBeanDefinition registrarDefinition = new RootBeanDefinition();

            registrarDefinition.setBeanClass(PreloadableEmbeddedPostgresRegistrar.class);
            registrarDefinition.getConstructorArgumentValues()
                    .addIndexedArgumentValue(0, databaseAnnotations);

            registry.registerBeanDefinition("preloadableEmbeddedPostgresRegistrar", registrarDefinition);
        }

        // TODO
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PreloadableEmbeddedPostgresContextCustomizer that =
                    (PreloadableEmbeddedPostgresContextCustomizer) o;

            return databaseAnnotations.equals(that.databaseAnnotations);
        }

        // TODO
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
                    PreloadableEmbeddedPostgresContextCustomizer.class.getSimpleName(),
                    ImmutableMap.of("spring.test.database.replace", "NONE")));
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // nothing to do
        }
    }

    protected static class PreloadableEmbeddedPostgresRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

        private final Set<AutoConfigureEmbeddedDatabase> databaseAnnotations;

        private Environment environment;

        public PreloadableEmbeddedPostgresRegistrar(Set<AutoConfigureEmbeddedDatabase> databaseAnnotations) {
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

            registerBeanIfMissing(registry, "defaultDatabaseProvider", PrefetchingDatabaseProvider.class);

            if (ClassUtils.isPresent("org.testcontainers.containers.PostgreSQLContainer", null)) {
                registerBeanIfMissing(registry, "dockerPostgresProvider", DockerPostgresDatabaseProvider.class);
            }
            if (ClassUtils.isPresent("io.zonky.test.db.postgres.embedded.EmbeddedPostgres", null)) {
                registerBeanIfMissing(registry, "zonkyPostgresProvider", ZonkyPostgresDatabaseProvider.class);
            }
            if (ClassUtils.isPresent("com.opentable.db.postgres.embedded.EmbeddedPostgres", null)) {
                registerBeanIfMissing(registry, "openTablePostgresProvider", OpenTablePostgresDatabaseProvider.class);
            }
            if (ClassUtils.isPresent("ru.yandex.qatools.embed.postgresql.EmbeddedPostgres", null)) {
                registerBeanIfMissing(registry, "yandexPostgresProvider", YandexPostgresDatabaseProvider.class);
            }

            for (AutoConfigureEmbeddedDatabase databaseAnnotation : databaseAnnotations) {
                DatabaseDescriptor databaseDescriptor = resolveDatabaseDescriptor(environment, databaseAnnotation);

                BeanDefinitionHolder dataSourceInfo = getDataSourceBeanDefinition(beanFactory, databaseAnnotation);
                BeanDefinitionHolder flywayInfo = getFlywayBeanDefinition(beanFactory);

                RootBeanDefinition dataSourceDefinition = new RootBeanDefinition();
                dataSourceDefinition.setPrimary(dataSourceInfo.getBeanDefinition().isPrimary());

                if (flywayInfo == null || databaseAnnotations.size() > 1) {
                    dataSourceDefinition.setBeanClass(EmptyEmbeddedPostgresDataSourceFactoryBean.class);
                    dataSourceDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, databaseDescriptor);
                } else {
                    BeanDefinitionHolder contextInfo = getDataSourceContextBeanDefinition(beanFactory, flywayInfo.getBeanName());

                    if (contextInfo == null) {
                        RootBeanDefinition dataSourceContextDefinition = new RootBeanDefinition();
                        dataSourceContextDefinition.setBeanClass(DefaultFlywayDataSourceContext.class);
                        registry.registerBeanDefinition("defaultDataSourceContext", dataSourceContextDefinition);
                        contextInfo = new BeanDefinitionHolder(dataSourceContextDefinition, "defaultDataSourceContext");
                    }

                    contextInfo.getBeanDefinition().getPropertyValues().addPropertyValue("descriptor", databaseDescriptor);

                    dataSourceDefinition.setBeanClass(FlywayEmbeddedPostgresDataSourceFactoryBean.class);
                    dataSourceDefinition.getConstructorArgumentValues()
                            .addIndexedArgumentValue(0, flywayInfo.getBeanName());
                    dataSourceDefinition.getConstructorArgumentValues()
                            .addIndexedArgumentValue(1, contextInfo.getBeanName());
                }

                String dataSourceBeanName = dataSourceInfo.getBeanName();
                if (registry.containsBeanDefinition(dataSourceBeanName)) {
                    logger.info("Replacing '{}' DataSource bean with embedded version", dataSourceBeanName);
                    registry.removeBeanDefinition(dataSourceBeanName);
                }
                registry.registerBeanDefinition(dataSourceBeanName, dataSourceDefinition);
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // nothing to do
        }

        protected DatabaseDescriptor resolveDatabaseDescriptor(Environment environment, AutoConfigureEmbeddedDatabase databaseAnnotation) {
            String providerName = databaseAnnotation.provider() != DEFAULT ? databaseAnnotation.provider().name() :
                    environment.getProperty("zonky.test.database.provider", ProviderType.ZONKY.toString());
            return new DatabaseDescriptor(io.zonky.test.db.provider.DatabaseType.POSTGRES, ProviderType.valueOf(providerName));
        }
    }

    protected static void registerBeanIfMissing(BeanDefinitionRegistry registry, String beanName, Class<?> beanClass) {
        if (!registry.containsBeanDefinition(beanName)) {
            RootBeanDefinition providerDefinition = new RootBeanDefinition(beanClass);
            registry.registerBeanDefinition(beanName, providerDefinition);
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

    protected static BeanDefinitionHolder getDataSourceContextBeanDefinition(ConfigurableListableBeanFactory beanFactory, String flywayName) {
        String[] beanNames = beanFactory.getBeanNamesForType(FlywayDataSourceContext.class, true, false);

        if (ObjectUtils.isEmpty(beanNames)) {
            return null;
        }

        if (beanNames.length == 1) {
            String beanName = beanNames[0];
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            return new BeanDefinitionHolder(beanDefinition, beanName);
        }

        if (beanFactory.containsBean(flywayName + "DataSourceContext")) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(flywayName + "DataSourceContext");
            return new BeanDefinitionHolder(beanDefinition, flywayName + "DataSourceContext");
        }

        return null;
    }

    protected static BeanDefinitionHolder getFlywayBeanDefinition(ConfigurableListableBeanFactory beanFactory) {
        String[] beanNames = beanFactory.getBeanNamesForType(Flyway.class, true, false);

        if (beanNames.length == 1) {
            String beanName = beanNames[0];
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            return new BeanDefinitionHolder(beanDefinition, beanName);
        }

        return null;
    }

    protected static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}
