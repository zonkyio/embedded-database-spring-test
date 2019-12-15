package io.zonky.test.db.flyway;

import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class FlywayExtensionTestExecutionListener implements TestExecutionListener, Ordered {

    @Override
    public int getOrder() {
        return 4001;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        processFlywayOperations(testContext);
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
        processFlywayOperations(testContext);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        processFlywayOperations(testContext);
    }

    @Override
    public void beforeTestExecution(TestContext testContext) {
        processFlywayOperations(testContext);
    }

    private static void processFlywayOperations(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        FlywayContextExtension flywayExtension = applicationContext.getBean(FlywayContextExtension.class);
        flywayExtension.processPendingOperations();
    }
}
