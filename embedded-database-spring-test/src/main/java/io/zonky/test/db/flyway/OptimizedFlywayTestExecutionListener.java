package io.zonky.test.db.flyway;

import org.flywaydb.test.junit.FlywayTestExecutionListener;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class OptimizedFlywayTestExecutionListener implements TestExecutionListener, Ordered {

    private final FlywayTestExecutionListener listener = new FlywayTestExecutionListener();

    @Override
    public int getOrder() {
        return 3999;
    }

    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        listener.beforeTestClass(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        listener.prepareTestInstance(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        listener.beforeTestMethod(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void beforeTestExecution(TestContext testContext) throws Exception {
        listener.beforeTestExecution(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void afterTestExecution(TestContext testContext) throws Exception {
        listener.afterTestExecution(testContext);
    }

    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
        listener.afterTestMethod(testContext);
    }

    @Override
    public void afterTestClass(TestContext testContext) throws Exception {
        listener.afterTestClass(testContext);
    }

    private static void processPendingFlywayOperations(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();

        if (applicationContext.getBeanNamesForType(FlywayExtension.class, false, false).length > 0) {
            FlywayExtension flywayExtension = applicationContext.getBean(FlywayExtension.class);
            flywayExtension.processPendingOperations();
        }
    }
}
