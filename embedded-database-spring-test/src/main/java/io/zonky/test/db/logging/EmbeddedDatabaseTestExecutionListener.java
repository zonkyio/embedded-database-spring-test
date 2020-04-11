package io.zonky.test.db.logging;

import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.provider.EmbeddedDatabase;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;

public class EmbeddedDatabaseTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestClass(TestContext testContext) {
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        Method testMethod = testContext.getTestMethod();
        ApplicationContext applicationContext = testContext.getApplicationContext();

        Map<String, DataSourceContext> dataSourceContexts = applicationContext
                .getBeansOfType(DataSourceContext.class, false, false);

        for (Entry<String, DataSourceContext> entry : dataSourceContexts.entrySet()) {
            String beanName = StringUtils.substringBeforeLast(entry.getKey(), "Context");
            EmbeddedDatabase database = (EmbeddedDatabase) entry.getValue().getTarget();
            EmbeddedDatabaseReporter.reportDataSource(beanName, database, testMethod);
        }
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
    }

    @Override
    public void afterTestClass(TestContext testContext) {
    }
}
