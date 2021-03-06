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

package io.zonky.test.db.support;

import com.google.common.collect.ImmutableMap;
import io.zonky.test.db.config.MissingDatabaseProviderException;
import io.zonky.test.db.config.Provider;
import io.zonky.test.db.provider.DatabaseProvider;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.core.type.MethodMetadata;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseProviders {

    private final Map<ProviderDescriptor, ObjectFactory<DatabaseProvider>> databaseProviders;

    public DatabaseProviders(ConfigurableListableBeanFactory beanFactory) {
        ImmutableMap.Builder<ProviderDescriptor, ObjectFactory<DatabaseProvider>> builder = ImmutableMap.builder();

        String[] beanNames = beanFactory.getBeanNamesForType(DatabaseProvider.class, true, false);
        for (String beanName : beanNames) {
            ProviderDescriptor descriptor = resolveDescriptor(beanFactory, beanName);
            if (descriptor != null) {
                builder.put(descriptor, () -> beanFactory.getBean(beanName, DatabaseProvider.class));
            }
        }

        this.databaseProviders = builder.build();
    }

    public DatabaseProvider getProvider(ProviderDescriptor descriptor) {
        ObjectFactory<DatabaseProvider> factory = databaseProviders.get(descriptor);

        if (factory == null) {
            List<String> availableProviders = databaseProviders.keySet().stream()
                    .filter(d -> d.getDatabaseName().equals(descriptor.getDatabaseName()))
                    .map(ProviderDescriptor::getProviderName)
                    .collect(Collectors.toList());

            throw new MissingDatabaseProviderException(descriptor, availableProviders);
        }

        return factory.getObject();
    }

    private static ProviderDescriptor resolveDescriptor(ConfigurableListableBeanFactory beanFactory, String beanName) {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);

        if (!beanDefinition.isAbstract() && beanDefinition instanceof AbstractBeanDefinition) {
            AutowireCandidateQualifier qualifier = ((AbstractBeanDefinition) beanDefinition).getQualifier(Provider.class.getName());
            if (qualifier != null) {
                String providerType = (String) qualifier.getAttribute("type");
                String databaseType = (String) qualifier.getAttribute("database");
                return ProviderDescriptor.of(providerType, databaseType);
            }
        }

        if (!beanDefinition.isAbstract() && beanDefinition instanceof AnnotatedBeanDefinition) {
            MethodMetadata factoryMethodMetadata = ((AnnotatedBeanDefinition) beanDefinition).getFactoryMethodMetadata();
            if (factoryMethodMetadata != null) {
                Map<String, Object> qualifier = factoryMethodMetadata.getAnnotationAttributes(Provider.class.getName(), true);
                if (qualifier != null) {
                    String providerType = (String) qualifier.get("type");
                    String databaseType = (String) qualifier.get("database");
                    return ProviderDescriptor.of(providerType, databaseType);
                }
            }
        }

        return null;
    }
}
