package io.zonky.test.db.event;

import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.ClassUtils;

public class EventPublishingTestExecutionListener extends AbstractTestExecutionListener {

    private static final boolean TEST_EXECUTION_METHODS_SUPPORTED = ClassUtils.hasMethod(
            TestExecutionListener.class, "beforeTestExecution", null);

    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (!TEST_EXECUTION_METHODS_SUPPORTED) {
            beforeTestExecution(testContext);
        }
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        if (!TEST_EXECUTION_METHODS_SUPPORTED) {
            afterTestExecution(testContext);
        }
    }

    @Override
    public void beforeTestExecution(TestContext testContext) {
        ApplicationContext applicationContext;
        try {
            applicationContext = testContext.getApplicationContext();
        } catch (IllegalStateException e) {
            return;
        }

        applicationContext.publishEvent(new TestExecutionStartedEvent(applicationContext, testContext.getTestMethod()));
    }

    @Override
    public void afterTestExecution(TestContext testContext) {
        ApplicationContext applicationContext;
        try {
            applicationContext = testContext.getApplicationContext();
        } catch (IllegalStateException e) {
            return;
        }

        applicationContext.publishEvent(new TestExecutionFinishedEvent(applicationContext, testContext.getTestMethod()));
    }
}
