package io.zonky.test.db.event;

import org.springframework.context.ApplicationEvent;

import java.lang.reflect.Method;

public class TestExecutionFinishedEvent extends ApplicationEvent {

    private final Method testMethod;

    public TestExecutionFinishedEvent(Object source, Method testMethod) {
        super(source);
        this.testMethod = testMethod;
    }

    public Method getTestMethod() {
        return testMethod;
    }
}
