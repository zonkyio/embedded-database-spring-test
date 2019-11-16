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

import com.google.common.collect.ObjectArrays;
import io.zonky.test.db.logging.EmbeddedDatabaseReporter;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.database.DatabaseFactory;
import org.flywaydb.core.internal.util.scanner.Scanner;
import org.flywaydb.test.annotation.FlywayTest;
import org.flywaydb.test.junit.FlywayTestExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.TestContext;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static io.zonky.test.db.util.ReflectionUtils.getField;
import static io.zonky.test.db.util.ReflectionUtils.invokeConstructor;
import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;
import static io.zonky.test.db.util.ReflectionUtils.invokeStaticMethod;

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

    private static final ClassLoader classLoader = OptimizedFlywayTestExecutionListener.class.getClassLoader();
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
        try {
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
                        originalDbReset(testContext, adjustedAnnotation);

                        return;
                    }
                }
            }

            originalDbReset(testContext, annotation);
        } catch (NoSuchMethodError e) {
            logger.error("\n\nHINT: Check that you are using compatible versions of org.flywaydb:flyway-core and org.flywaydb.flyway-test-extensions:flyway-spring-test dependencies!!!\n");
            throw e;
        }
    }

    protected void originalDbReset(TestContext testContext, FlywayTest annotation) {
        try {
            String dbResetMethodName = repeatableAnnotationPresent ? "dbResetWithAnnotation" : "dbResetWithAnotation";
            invokeMethod(this, dbResetMethodName, testContext, annotation);
        } catch (FlywayException e) {
            if (e.getCause() instanceof SQLException) {
                String errorCode = ((SQLException) e.getCause()).getSQLState();
                if (errorCode != null && errorCode.matches("(42723|42P06|42P07|42712|42710)")) {
                    logger.error("\n\nHINT: Check that you have correctly set org.flywaydb.core.Flyway#schemaNames property!!!\n");
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
                    setFlywayLocations(flywayBean, annotation.locationsForMigrate());
                } else {
                    setFlywayLocations(flywayBean, ObjectArrays.concat(oldLocations, annotation.locationsForMigrate(), String.class));
                }
                return dataSourceContext.reload(flywayBean).get();
            } finally {
                setFlywayLocations(flywayBean, oldLocations);
            }
        }
    }

    /**
     * Checks if test migrations are appendable to core migrations.
     */
    protected static boolean isAppendable(Flyway flyway, FlywayTest annotation) throws ClassNotFoundException {
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

    protected static MigrationVersion findFirstVersion(Flyway flyway, String... locations) throws ClassNotFoundException {
        Collection<ResolvedMigration> migrations = resolveMigrations(flyway, locations);
        return migrations.stream()
                .filter(migration -> migration.getVersion() != null)
                .findFirst()
                .map(ResolvedMigration::getVersion)
                .orElse(MigrationVersion.EMPTY);
    }

    protected static MigrationVersion findLastVersion(Flyway flyway, String... locations) throws ClassNotFoundException {
        Collection<ResolvedMigration> migrations = resolveMigrations(flyway, locations);
        return migrations.stream()
                .filter(migration -> migration.getVersion() != null)
                .reduce((first, second) -> second) // finds last item
                .map(ResolvedMigration::getVersion)
                .orElse(MigrationVersion.EMPTY);
    }

    protected static Collection<ResolvedMigration> resolveMigrations(Flyway flyway, String... locations) throws ClassNotFoundException {
        MigrationResolver resolver = createMigrationResolver(flyway, locations);

        if (flywayVersion >= 52) {
            Object configInstance = getField(flyway, "configuration");
            Class<?> contextType = ClassUtils.forName("org.flywaydb.core.api.resolver.Context", classLoader);
            Object contextInstance = ProxyFactory.getProxy(contextType, (MethodInterceptor) invocation ->
                    "getConfiguration".equals(invocation.getMethod().getName()) ? configInstance : invocation.proceed());
            return invokeMethod(resolver, "resolveMigrations", contextInstance);
        } else {
            return resolver.resolveMigrations();
        }
    }

    protected static MigrationResolver createMigrationResolver(Flyway flyway, String... locations) throws ClassNotFoundException {
        String[] oldLocations = getFlywayLocations(flyway);
        try {
            setFlywayLocations(flyway, locations);

            if (flywayVersion >= 60) {
                Object configuration = getField(flyway, "configuration");
                Object jdbcConnectionFactory = invokeConstructor("org.flywaydb.core.internal.jdbc.JdbcConnectionFactory", invokeMethod(configuration, "getDataSource"), 0);
                Object sqlScriptFactory = invokeStaticMethod(DatabaseFactory.class, "createSqlScriptFactory", jdbcConnectionFactory, configuration);
                Object sqlScriptExecutorFactory = invokeStaticMethod(DatabaseFactory.class, "createSqlScriptExecutorFactory", jdbcConnectionFactory);
                Object scanner;

                try {
                    scanner = invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                            ClassUtils.forName("org.flywaydb.core.api.migration.JavaMigration", classLoader),
                            Arrays.asList((Object[]) invokeMethod(configuration, "getLocations")),
                            invokeMethod(configuration, "getClassLoader"),
                            invokeMethod(configuration, "getEncoding"));
                } catch (RuntimeException ex) {
                    scanner = invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                            Arrays.asList((Object[]) invokeMethod(configuration, "getLocations")),
                            invokeMethod(configuration, "getClassLoader"),
                            invokeMethod(configuration, "getEncoding"));
                }

                return invokeMethod(flyway, "createMigrationResolver", scanner, scanner, sqlScriptExecutorFactory, sqlScriptFactory);
            } else if (flywayVersion >= 52) {
                Object configuration = getField(flyway, "configuration");
                Object database = invokeStaticMethod(DatabaseFactory.class, "createDatabase", flyway, false);
                Object factory = invokeMethod(database, "createSqlStatementBuilderFactory");
                Object scanner = invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                        Arrays.asList((Object[]) invokeMethod(configuration, "getLocations")),
                        invokeMethod(configuration, "getClassLoader"),
                        invokeMethod(configuration, "getEncoding"));
                return invokeMethod(flyway, "createMigrationResolver", database, scanner, scanner, factory);
            } else if (flywayVersion >= 51) {
                Object configuration = getField(flyway, "configuration");
                Object scanner = invokeConstructor(Scanner.class, configuration);
                Object placeholderReplacer = invokeMethod(flyway, "createPlaceholderReplacer");
                return invokeMethod(flyway, "createMigrationResolver", null, scanner, placeholderReplacer);
            } else if (flywayVersion >= 40) {
                Scanner scanner = new Scanner(flyway.getClassLoader());
                return invokeMethod(flyway, "createMigrationResolver", null, scanner);
            } else {
                return invokeMethod(flyway, "createMigrationResolver", (Object) null);
            }
        } finally {
            setFlywayLocations(flyway, oldLocations);
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
        if (flywayVersion >= 51) {
            Object configuration = getField(flyway, "configuration");
            return Arrays.stream((Object[]) invokeMethod(configuration, "getLocations"))
                    .map(location -> invokeMethod(location, "getDescriptor"))
                    .toArray(String[]::new);
        } else {
            return flyway.getLocations();
        }
    }

    protected static void setFlywayLocations(Flyway flyway, String[] locations) {
        if (flywayVersion >= 51) {
            Object configuration = getField(flyway, "configuration");
            invokeMethod(configuration, "setLocationsAsStrings", (Object) locations);
        } else {
            flyway.setLocations(locations);
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
