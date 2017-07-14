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

package io.zonky.test.db.flyway;

import com.google.common.collect.Iterables;
import com.google.common.collect.ObjectArrays;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.resolver.CompositeMigrationResolver;
import org.flywaydb.core.internal.util.ConfigurationInjectionUtils;
import org.flywaydb.core.internal.util.Locations;
import org.flywaydb.core.internal.util.PlaceholderReplacer;
import org.flywaydb.core.internal.util.scanner.Scanner;
import org.flywaydb.test.annotation.FlywayTest;
import org.flywaydb.test.annotation.FlywayTests;
import org.flywaydb.test.junit.FlywayTestExecutionListener;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Optimized implementation of the {@link org.flywaydb.test.junit.FlywayTestExecutionListener}
 * that takes advantage of the fact that the reloading of the database through the
 * <code>FlywayDataSourceContext#reload(org.flywaydb.core.Flyway)</code> method can be quick and cheap operation.
 * However, it is necessary to fulfill the condition that the same data has already been loaded.
 * In such cases the {@link FlywayDataSourceContext} utilizes a special template database
 * to effective copy data into multiple independent databases.
 *
 * @see <a href="https://www.postgresql.org/docs/9.6/static/manage-ag-templatedbs.html">Template Databases</a>
 */
public class OptimizedFlywayTestExecutionListener extends FlywayTestExecutionListener {

    /**
     * The order value must be less than {@link org.springframework.test.context.transaction.TransactionalTestExecutionListener}.
     * Otherwise, the flyway initialization can result in non-deterministic behavior related to transaction isolation.
     */
    private int order = 3900;

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        Class<?> testClass = testContext.getTestClass();

        FlywayTests containerAnnotation = AnnotationUtils.findAnnotation(testClass, FlywayTests.class);
        if (containerAnnotation != null) {
            FlywayTest[] annotations = containerAnnotation.value();
            for (FlywayTest annotation : annotations) {
                optimizedDbReset(testContext, annotation);
            }
        } else {
            FlywayTest annotation = AnnotationUtils.findAnnotation(testClass, FlywayTest.class);
            optimizedDbReset(testContext, annotation);
        }
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        Method testMethod = testContext.getTestMethod();

        FlywayTests containerAnnotation = AnnotationUtils.findAnnotation(testMethod, FlywayTests.class);
        if (containerAnnotation != null) {
            FlywayTest[] annotations = containerAnnotation.value();
            for (FlywayTest annotation : annotations) {
                optimizedDbReset(testContext, annotation);
            }
        } else {
            FlywayTest annotation = AnnotationUtils.findAnnotation(testMethod, FlywayTest.class);
            optimizedDbReset(testContext, annotation);
        }
    }

    private void optimizedDbReset(TestContext testContext, FlywayTest annotation) throws Exception {
        if (annotation != null && annotation.invokeCleanDB() && annotation.invokeMigrateDB() && !annotation.invokeBaselineDB()) {

            ApplicationContext applicationContext = testContext.getApplicationContext();

            FlywayDataSourceContext dataSourceContext = getDataSourceContext(applicationContext, annotation.flywayName());
            Flyway flywayBean = ReflectionTestUtils.invokeMethod(this, "getBean", applicationContext, Flyway.class, annotation.flywayName());

            if (dataSourceContext != null && flywayBean != null) {
                prepareDataSourceContext(dataSourceContext, flywayBean, annotation);

                FlywayTest adjustedAnnotation = copyAnnotation(annotation, false, false, true);
                ReflectionTestUtils.invokeMethod(this, "dbResetWithAnnotation", testContext, adjustedAnnotation);

                return;
            }
        }

        ReflectionTestUtils.invokeMethod(this, "dbResetWithAnnotation", testContext, annotation);
    }

    private static void prepareDataSourceContext(FlywayDataSourceContext dataSourceContext, Flyway flywayBean, FlywayTest annotation) throws Exception {
        if (isAppendable(flywayBean, annotation)) {
            dataSourceContext.reload(flywayBean);
        } else {
            String[] oldLocations = flywayBean.getLocations();
            try {
                if (annotation.overrideLocations()) {
                    flywayBean.setLocations(annotation.locationsForMigrate());
                } else {
                    flywayBean.setLocations(ObjectArrays.concat(oldLocations, annotation.locationsForMigrate(), String.class));
                }
                dataSourceContext.reload(flywayBean);
            } finally {
                flywayBean.setLocations(oldLocations);
            }
        }
    }

    /**
     * Checks if test migrations are appendable to core migrations.
     */
    private static boolean isAppendable(Flyway flyway, FlywayTest annotation) {
        if (annotation.overrideLocations()) {
            return false;
        }

        if (ArrayUtils.isEmpty(annotation.locationsForMigrate())) {
            return true;
        }

        MigrationVersion testVersion = findLastVersion(flyway, annotation.locationsForMigrate());
        if (testVersion == MigrationVersion.EMPTY) {
            return true;
        }

        MigrationVersion coreVersion = findLastVersion(flyway, flyway.getLocations());
        return coreVersion.compareTo(testVersion) < 0;
    }

    private static MigrationVersion findLastVersion(Flyway flyway, String... locations) {
        CompositeMigrationResolver resolver = createMigrationResolver(flyway, locations);
        List<ResolvedMigration> migrations = resolver.resolveMigrations();

        if (CollectionUtils.isEmpty(migrations)) {
            return MigrationVersion.EMPTY;
        } else {
            return Iterables.getLast(migrations).getVersion();
        }
    }

    private static CompositeMigrationResolver createMigrationResolver(Flyway flyway, String... locations) {
        Scanner scanner = new Scanner(flyway.getClassLoader());

        for (MigrationResolver resolver : flyway.getResolvers()) {
            ConfigurationInjectionUtils.injectFlywayConfiguration(resolver, flyway);
        }

        return new CompositeMigrationResolver(null, scanner, flyway,
                new Locations(locations), createPlaceholderReplacer(flyway), flyway.getResolvers());
    }

    private static PlaceholderReplacer createPlaceholderReplacer(Flyway flyway) {
        if (flyway.isPlaceholderReplacement()) {
            return new PlaceholderReplacer(flyway.getPlaceholders(), flyway.getPlaceholderPrefix(), flyway.getPlaceholderSuffix());
        }
        return PlaceholderReplacer.NO_PLACEHOLDERS;
    }

    private static FlywayDataSourceContext getDataSourceContext(ApplicationContext context, String flywayName) {
        try {
            if (StringUtils.isBlank(flywayName)) {
                return context.getBean(FlywayDataSourceContext.class);
            } else {
                return context.getBean(flywayName + "DataSourceContext", FlywayDataSourceContext.class);
            }
        } catch (BeansException e) {
            return null;
        }
    }

    private static FlywayTest copyAnnotation(FlywayTest annotation, boolean invokeCleanDB, boolean invokeBaselineDB, boolean invokeMigrateDB) {
        Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
        attributes.put("invokeCleanDB", invokeCleanDB);
        attributes.put("invokeBaselineDB", invokeBaselineDB);
        attributes.put("invokeMigrateDB", invokeMigrateDB);
        return AnnotationUtils.synthesizeAnnotation(attributes, FlywayTest.class, null);
    }
}
