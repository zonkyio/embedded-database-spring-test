package io.zonky.test.db.event;

import org.springframework.context.ApplicationEvent;

import java.lang.reflect.Method;

public class TestExecutionStartedEvent extends ApplicationEvent {

    private final Method testMethod;

    public TestExecutionStartedEvent(Object source, Method testMethod) {
        super(source);
        this.testMethod = testMethod;
    }

    public Method getTestMethod() {
        return testMethod;
    }
}
