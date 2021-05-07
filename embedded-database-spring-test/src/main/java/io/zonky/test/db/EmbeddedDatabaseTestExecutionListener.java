package io.zonky.test.db;

import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.util.AnnotationUtils;
import io.zonky.test.db.util.PropertyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Conventions;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.Replace;

public class EmbeddedDatabaseTestExecutionListener extends AbstractTestExecutionListener {

    private static final String TEST_PREPARERS_ATTRIBUTE_PREFIX = Conventions.getQualifiedAttributeName(
            EmbeddedDatabaseTestExecutionListener.class, "testClassPreparers");

    @Override
    public int getOrder() {
        return 3800;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        resetDatabases(testContext, RefreshMode.BEFORE_CLASS, RefreshMode.BEFORE_EACH_TEST_METHOD);
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
        captureTestClassPreparers(testContext, RefreshMode.BEFORE_EACH_TEST_METHOD, RefreshMode.AFTER_EACH_TEST_METHOD);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        resetDatabasesAndApplyTestClassPreparers(testContext, RefreshMode.BEFORE_EACH_TEST_METHOD);
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        resetDatabasesAndApplyTestClassPreparers(testContext, RefreshMode.AFTER_EACH_TEST_METHOD);
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        resetDatabases(testContext, RefreshMode.AFTER_CLASS, RefreshMode.AFTER_EACH_TEST_METHOD);
    }

    private void resetDatabases(TestContext testContext, RefreshMode... refreshModes) {
        forEachDatabase(testContext, refreshModes, (context, annotation) -> {
            context.reset();
        });
    }

    private void resetDatabasesAndApplyTestClassPreparers(TestContext testContext, RefreshMode... refreshModes) {
        forEachDatabase(testContext, refreshModes, (context, annotation) -> {
            context.reset();

            String attributeFullName = getTestPreparersAttributeName(testContext, annotation.beanName());
            if (testContext.hasAttribute(attributeFullName)) {
                List<DatabasePreparer> testPreparers = (List<DatabasePreparer>) testContext.getAttribute(attributeFullName);
                for (DatabasePreparer testPreparer : testPreparers) {
                    context.apply(testPreparer);
                }
            }
        });
    }

    private void captureTestClassPreparers(TestContext testContext, RefreshMode... refreshModes) {
        forEachDatabase(testContext, refreshModes, (context, annotation) -> {
            String attributeFullName = getTestPreparersAttributeName(testContext, annotation.beanName());
            if (!testContext.hasAttribute(attributeFullName)) {
                List<DatabasePreparer> testPreparers = context.getTestPreparers();
                testContext.setAttribute(attributeFullName, testPreparers);
            }
        });
    }

    private void forEachDatabase(TestContext testContext, RefreshMode[] refreshModes, BiConsumer<DatabaseContext, AutoConfigureEmbeddedDatabase> action) {
        Set<AutoConfigureEmbeddedDatabase> annotations = AnnotationUtils.getDatabaseAnnotations(testContext.getTestClass());

        ApplicationContext applicationContext = testContext.getApplicationContext();
        Environment environment = applicationContext.getEnvironment();

        annotations.stream()
                .filter(annotation -> isEnabled(annotation, environment))
                .filter(annotation -> hasAnyRefreshMode(annotation, refreshModes, environment))
                .forEach(annotation -> {
                    DatabaseContext databaseContext = getDatabaseContext(applicationContext, annotation.beanName());
                    action.accept(databaseContext, annotation);
                });
    }

    private boolean isEnabled(AutoConfigureEmbeddedDatabase annotation, Environment environment) {
        Replace replace = annotation.replace() == Replace.NONE ? Replace.NONE :
                PropertyUtils.getEnumProperty(environment, "zonky.test.database.replace", Replace.class, Replace.ANY);
        return replace != Replace.NONE;
    }

    private boolean hasAnyRefreshMode(AutoConfigureEmbeddedDatabase annotation, RefreshMode[] refreshModes, Environment environment) {
        RefreshMode currentMode = annotation.refresh() != RefreshMode.NEVER ? annotation.refresh() :
                PropertyUtils.getEnumProperty(environment, "zonky.test.database.refresh", RefreshMode.class, RefreshMode.NEVER);
        return Arrays.stream(refreshModes).anyMatch(mode -> mode == currentMode);
    }

    private DatabaseContext getDatabaseContext(ApplicationContext applicationContext, String beanName) {
        if (StringUtils.hasText(beanName)) {
            String databaseContextBeanName = beanName + "Context";
            return applicationContext.getBean(databaseContextBeanName, DatabaseContext.class);
        } else {
            return applicationContext.getBean(DatabaseContext.class);
        }
    }

    private String getTestPreparersAttributeName(TestContext testContext, String beanName) {
        String attributeQualifier = Conventions.getQualifiedAttributeName(testContext.getTestClass(), beanName);
        return TEST_PREPARERS_ATTRIBUTE_PREFIX + '.' + attributeQualifier;
    }
}
