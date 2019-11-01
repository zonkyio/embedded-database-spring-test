package io.zonky.test.db.provider.config;

import com.google.common.collect.ImmutableMap;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.MissingDatabaseProviderException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.core.type.MethodMetadata;

import java.util.Map;

public class DatabaseProviders {

    private final Map<DatabaseDescriptor, ObjectFactory<DatabaseProvider>> databaseProviders;

    public DatabaseProviders(ConfigurableListableBeanFactory beanFactory) {
        ImmutableMap.Builder<DatabaseDescriptor, ObjectFactory<DatabaseProvider>> builder = ImmutableMap.builder();

        String[] beanNames = beanFactory.getBeanNamesForType(DatabaseProvider.class, true, false);
        for (String beanName : beanNames) {
            DatabaseDescriptor descriptor = resolveDescriptor(beanFactory, beanName);
            if (descriptor != null) {
                builder.put(descriptor, () -> beanFactory.getBean(beanName, DatabaseProvider.class));
            }
        }

        this.databaseProviders = builder.build();
    }

    public DatabaseProvider getProvider(DatabaseDescriptor descriptor) {
        ObjectFactory<DatabaseProvider> factory = databaseProviders.get(descriptor);

        if (factory == null) {
            // TODO: it should print available providers
            throw new MissingDatabaseProviderException(descriptor);
        }

        return factory.getObject();
    }

    private static DatabaseDescriptor resolveDescriptor(ConfigurableListableBeanFactory beanFactory, String beanName) {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);

        if (!beanDefinition.isAbstract() && beanDefinition instanceof AbstractBeanDefinition) {
            AutowireCandidateQualifier qualifier = ((AbstractBeanDefinition) beanDefinition).getQualifier(Provider.class.getName());
            if (qualifier != null) {
                String providerType = (String) qualifier.getAttribute("type");
                String databaseType = (String) qualifier.getAttribute("database");
                return new DatabaseDescriptor(databaseType, providerType);
            }
        }

        if (!beanDefinition.isAbstract() && beanDefinition instanceof AnnotatedBeanDefinition) {
            MethodMetadata factoryMethodMetadata = ((AnnotatedBeanDefinition) beanDefinition).getFactoryMethodMetadata();
            if (factoryMethodMetadata != null) {
                Map<String, Object> qualifier = factoryMethodMetadata.getAnnotationAttributes(Provider.class.getName(), true);
                if (qualifier != null) {
                    String providerType = (String) qualifier.get("type");
                    String databaseType = (String) qualifier.get("database");
                    return new DatabaseDescriptor(databaseType, providerType);
                }
            }
        }

        return null;
    }
}
