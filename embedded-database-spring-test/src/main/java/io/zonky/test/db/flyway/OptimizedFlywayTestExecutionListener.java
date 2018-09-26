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
import io.zonky.test.db.logging.EmbeddedDatabaseReporter;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.util.scanner.Scanner;
import org.flywaydb.test.annotation.FlywayTest;
import org.flywaydb.test.junit.FlywayTestExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.TestContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.invokeMethod;

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
public class OptimizedFlywayTestExecutionListener extends FlywayTestExecutionListener implements Ordered {

    private static final Logger logger = LoggerFactory.getLogger(OptimizedFlywayTestExecutionListener.class);

    private static final boolean flywayNameAttributePresent = FlywayClassUtils.isFlywayNameAttributePresent();
    private static final boolean flywayBaselineAttributePresent = FlywayClassUtils.isFlywayBaselineAttributePresent();
    private static final boolean repeatableAnnotationPresent = FlywayClassUtils.isRepeatableFlywayTestAnnotationPresent();
    private static final int flywayVersion = FlywayClassUtils.getFlywayVersion();

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

        FlywayTest[] annotations = findFlywayTestAnnotations(testClass);
        if (annotations.length > 1) {
            logger.warn("Optimized database loading is not supported when using multiple flyway test annotations");
        }
        for (FlywayTest annotation : annotations) {
            optimizedDbReset(testContext, testClass, annotation);
        }
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        Method testMethod = testContext.getTestMethod();

        FlywayTest[] annotations = findFlywayTestAnnotations(testMethod);
        if (annotations.length > 1) {
            logger.warn("Optimized database loading is not supported when using multiple flyway test annotations");
        }
        for (FlywayTest annotation : annotations) {
            optimizedDbReset(testContext, testMethod, annotation);
        }
    }

    protected FlywayTest[] findFlywayTestAnnotations(AnnotatedElement element) {
        if (repeatableAnnotationPresent) {
            org.flywaydb.test.annotation.FlywayTests containerAnnotation = findAnnotation(element, org.flywaydb.test.annotation.FlywayTests.class);
            if (containerAnnotation != null) {
                return containerAnnotation.value();
            }
        }

        FlywayTest annotation = findAnnotation(element, FlywayTest.class);
        if (annotation != null) {
            return new FlywayTest[] { annotation };
        }

        return new FlywayTest[0];
    }

    protected synchronized void optimizedDbReset(TestContext testContext, AnnotatedElement element, FlywayTest annotation) throws Exception {
        String dbResetMethodName = repeatableAnnotationPresent ? "dbResetWithAnnotation" : "dbResetWithAnotation";

        if (annotation != null && annotation.invokeCleanDB() && annotation.invokeMigrateDB()
                && (!flywayBaselineAttributePresent || !annotation.invokeBaselineDB())) {

            ApplicationContext applicationContext = testContext.getApplicationContext();
            Flyway flywayBean = getFlywayBean(applicationContext, annotation);

            if (flywayBean != null) {
                FlywayDataSourceContext dataSourceContext = getDataSourceContext(applicationContext, flywayBean);

                if (dataSourceContext != null) {

                    dataSourceContext.getTarget(); // wait for completion of running flyway migration
                    DataSource dataSource = reloadDataSource(dataSourceContext, flywayBean, annotation);
                    EmbeddedDatabaseReporter.reportDataSource(dataSource, element);

                    FlywayTest adjustedAnnotation = copyAnnotation(annotation, false, false, true);
                    invokeMethod(this, dbResetMethodName, testContext, adjustedAnnotation);

                    return;
                }
            }
        }

        try {
            invokeMethod(this, dbResetMethodName, testContext, annotation);
        } catch (FlywayException e) {
            if (e.getCause() instanceof SQLException) {
                String errorCode = ((SQLException) e.getCause()).getSQLState();
                if (errorCode != null && errorCode.matches("(42723|42P06|42P07|42712|42710)")) {
                    logger.error("HINT: Check that you have correctly set org.flywaydb.core.Flyway#schemaNames property!!!");
                }
            }
            throw e;
        }
    }

    protected static DataSource reloadDataSource(FlywayDataSourceContext dataSourceContext, Flyway flywayBean, FlywayTest annotation) throws Exception {
        if (isAppendable(flywayBean, annotation)) {
            return dataSourceContext.reload(flywayBean).get();
        } else {
            String[] oldLocations = getFlywayLocations(flywayBean);
            try {
                if (annotation.overrideLocations()) {
                    flywayBean.setLocations(annotation.locationsForMigrate());
                } else {
                    flywayBean.setLocations(ObjectArrays.concat(oldLocations, annotation.locationsForMigrate(), String.class));
                }
                return dataSourceContext.reload(flywayBean).get();
            } finally {
                flywayBean.setLocations(oldLocations);
            }
        }
    }

    /**
     * Checks if test migrations are appendable to core migrations.
     */
    protected static boolean isAppendable(Flyway flyway, FlywayTest annotation) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (annotation.overrideLocations()) {
            return false;
        }

        if (ArrayUtils.isEmpty(annotation.locationsForMigrate())) {
            return true;
        }

        MigrationVersion testVersion = findFirstVersion(flyway, annotation.locationsForMigrate());
        if (testVersion == MigrationVersion.EMPTY) {
            return true;
        }

        MigrationVersion coreVersion = findLastVersion(flyway, getFlywayLocations(flyway));
        return coreVersion.compareTo(testVersion) < 0;
    }

    protected static MigrationVersion findFirstVersion(Flyway flyway, String... locations) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        MigrationResolver resolver = createMigrationResolver(flyway, locations);
        Collection<ResolvedMigration> migrations = resolver.resolveMigrations();

        if (CollectionUtils.isEmpty(migrations)) {
            return MigrationVersion.EMPTY;
        } else {
            return Iterables.getFirst(migrations, null).getVersion();
        }
    }

    protected static MigrationVersion findLastVersion(Flyway flyway, String... locations) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        MigrationResolver resolver = createMigrationResolver(flyway, locations);
        Collection<ResolvedMigration> migrations = resolver.resolveMigrations();

        if (CollectionUtils.isEmpty(migrations)) {
            return MigrationVersion.EMPTY;
        } else {
            return Iterables.getLast(migrations).getVersion();
        }
    }

    protected static MigrationResolver createMigrationResolver(Flyway flyway, String... locations) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        String[] oldLocations = getFlywayLocations(flyway);
        try {
            flyway.setLocations(locations);

            if (flywayVersion >= 51) {
                ClassLoader classLoader = OptimizedFlywayTestExecutionListener.class.getClassLoader();
                Class<?> configType = ClassUtils.forName("org.flywaydb.core.api.configuration.Configuration", classLoader);
                Object configInstance = getField(flyway, "configuration");
                Scanner scanner = Scanner.class.getDeclaredConstructor(configType).newInstance(configInstance);
                Object placeholderReplacer = invokeMethod(flyway, "createPlaceholderReplacer");
                return invokeMethod(flyway, "createMigrationResolver", null, scanner, placeholderReplacer);
            } else if (flywayVersion >= 40) {
                Scanner scanner = new Scanner(flyway.getClassLoader());
                return invokeMethod(flyway, "createMigrationResolver", null, scanner);
            } else {
                return invokeMethod(flyway, "createMigrationResolver", (Object) null);
            }
        } finally {
            flyway.setLocations(oldLocations);
        }
    }

    protected Flyway getFlywayBean(ApplicationContext applicationContext, FlywayTest annotation) {
        if (flywayNameAttributePresent) {
            return invokeMethod(this, "getBean", applicationContext, Flyway.class, annotation.flywayName());
        } else {
            return invokeMethod(this, "getBean", applicationContext, Flyway.class);
        }
    }

    protected static String[] getFlywayLocations(Flyway flyway) {
        try {
            if (flywayVersion >= 51) {
                return Arrays.stream((Object[]) invokeMethod(flyway, "getLocations"))
                        .map(location -> invokeMethod(location, "getDescriptor"))
                        .toArray(String[]::new);
            } else {
                return flyway.getLocations();
            }
        } catch (NoSuchMethodError e) {
            logger.error("HINT: Check that you are using compatible versions of org.flywaydb:flyway-core and org.flywaydb.flyway-test-extensions:flyway-spring-test dependencies!!!");
            throw e;
        }
    }

    protected static FlywayDataSourceContext getDataSourceContext(ApplicationContext context, Flyway flywayBean) {
        Map<String, Flyway> flywayBeans = context.getBeansOfType(Flyway.class);
        String flywayBeanName = flywayBeans.entrySet().stream()
                .filter(e -> e.getValue() == flywayBean)
                .map(Map.Entry::getKey)
                .findFirst().orElse("default");

        try {
            return context.getBean(flywayBeanName + "DataSourceContext", FlywayDataSourceContext.class);
        } catch (BeansException e) {}

        try {
            return context.getBean(FlywayDataSourceContext.class);
        } catch (BeansException e) {}

        return null;
    }

    /**
     * Customized implementation because {@link AnnotationUtils#findAnnotation(AnnotatedElement, Class)} method
     * operates generically on annotated elements and not execute specialized search algorithms for classes and methods.
     *
     * @see AnnotationUtils#findAnnotation(AnnotatedElement, Class)
     */
    protected static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
        if (annotatedElement instanceof Class<?>) {
            return AnnotationUtils.findAnnotation((Class<?>) annotatedElement, annotationType);
        } else if (annotatedElement instanceof Method) {
            return AnnotationUtils.findAnnotation((Method) annotatedElement, annotationType);
        } else {
            return AnnotationUtils.findAnnotation(annotatedElement, annotationType);
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
