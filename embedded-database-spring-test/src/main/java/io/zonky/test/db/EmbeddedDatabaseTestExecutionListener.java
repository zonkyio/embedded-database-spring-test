package io.zonky.test.db;

import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.util.AnnotationUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Conventions;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode;

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

    private void forEachDatabase(TestContext testContext, RefreshMode[] refreshModes, BiConsumer<DataSourceContext, AutoConfigureEmbeddedDatabase> action) {
        Set<AutoConfigureEmbeddedDatabase> annotations = AnnotationUtils.getDatabaseAnnotations(testContext.getTestClass());

        for (AutoConfigureEmbeddedDatabase annotation : annotations) {
            if (Arrays.stream(refreshModes).noneMatch(mode -> mode == annotation.refreshMode())) {
                continue;
            }

            ApplicationContext applicationContext;
            try {
                applicationContext = testContext.getApplicationContext();
            } catch (IllegalStateException e) {
                return;
            }

            DataSourceContext dataSourceContext = getDataSourceContext(applicationContext, annotation.beanName());
            action.accept(dataSourceContext, annotation);
        }
    }

    private DataSourceContext getDataSourceContext(ApplicationContext applicationContext, String beanName) {
        if (StringUtils.isBlank(beanName)) {
            return applicationContext.getBean(DataSourceContext.class);
        } else {
            String dataSourceContextBeanName = beanName + "Context";
            return applicationContext.getBean(dataSourceContextBeanName, DataSourceContext.class);
        }
    }

    private String getTestPreparersAttributeName(TestContext testContext, String beanName) {
        String attributeQualifier = Conventions.getQualifiedAttributeName(testContext.getTestClass(), beanName);
        return TEST_PREPARERS_ATTRIBUTE_PREFIX + '.' + attributeQualifier;
    }
}
