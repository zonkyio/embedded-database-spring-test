package io.zonky.test.support;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ConditionalTestRule implements TestRule {

    private final Runnable assumption;

    public ConditionalTestRule(Runnable assumption) {
        this.assumption = assumption;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assumption.run();
                base.evaluate();
            }
        };
    }
}
