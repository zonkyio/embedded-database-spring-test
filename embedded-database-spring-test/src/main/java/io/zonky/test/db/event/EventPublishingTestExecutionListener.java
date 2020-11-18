package io.zonky.test.db.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.ClassUtils;

public class EventPublishingTestExecutionListener extends AbstractTestExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(EventPublishingTestExecutionListener.class);

    private static final boolean TEST_EXECUTION_METHODS_SUPPORTED = ClassUtils.getMethodIfAvailable(
            TestExecutionListener.class, "beforeTestExecution", null) != null;

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
        ApplicationContext applicationContext = testContext.getApplicationContext();
        applicationContext.publishEvent(new TestExecutionStartedEvent(applicationContext, testContext.getTestMethod()));
        logger.trace("Test execution started - '{}#{}'", testContext.getTestClass().getSimpleName(), testContext.getTestMethod().getName());
    }

    @Override
    public void afterTestExecution(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        applicationContext.publishEvent(new TestExecutionFinishedEvent(applicationContext, testContext.getTestMethod()));
        logger.trace("Test execution finished - '{}#{}'", testContext.getTestClass().getSimpleName(), testContext.getTestMethod().getName());
    }
}
