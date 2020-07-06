package io.zonky.test.db;

import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.util.AnnotationUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.util.Set;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode;

public class EmbeddedDatabaseTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        return 3800;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        resetDatabases(testContext, RefreshMode.BEFORE_CLASS);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        resetDatabases(testContext, RefreshMode.BEFORE_EACH_TEST_METHOD);
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        resetDatabases(testContext, RefreshMode.AFTER_EACH_TEST_METHOD);
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        resetDatabases(testContext, RefreshMode.AFTER_CLASS);
    }

    private void resetDatabases(TestContext testContext, RefreshMode refreshMode) {
        Set<AutoConfigureEmbeddedDatabase> annotations = AnnotationUtils.getDatabaseAnnotations(testContext.getTestClass());

        for (AutoConfigureEmbeddedDatabase annotation : annotations) {
            if (annotation.refreshMode() != refreshMode) {
                continue;
            }

            ApplicationContext applicationContext;
            try {
                applicationContext = testContext.getApplicationContext();
            } catch (IllegalStateException e) {
                return;
            }

            if (StringUtils.isBlank(annotation.beanName())) {
                DataSourceContext dataSourceContext = applicationContext.getBean(DataSourceContext.class);
                dataSourceContext.reset();
            } else {
                String dataSourceContextBeanName = annotation.beanName() + "Context";
                DataSourceContext dataSourceContext = applicationContext.getBean(dataSourceContextBeanName, DataSourceContext.class);
                dataSourceContext.reset();
            }
        }
    }
}
