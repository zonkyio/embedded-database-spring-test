package io.zonky.test.db.event;

import io.zonky.test.category.SpringTestSuite;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(SpringTestSuite.class)
@ContextConfiguration
public class EventPublishingTestExecutionListenerTest {

    @Configuration
    static class Config {

        @Bean
        public TestExecutionContext testExecutionContext() {
            return new TestExecutionContext();
        }
    }

    @Autowired
    private TestExecutionContext testExecutionContext;

    @Test
    public void test1() {
        int count = testExecutionContext.executedTestCount.incrementAndGet();
        assertThat(testExecutionContext.executionStartedEvents.size()).isEqualTo(count);
        assertThat(testExecutionContext.executionFinishedEvents.size()).isEqualTo(count - 1);
    }

    @Test
    public void test2() {
        int count = testExecutionContext.executedTestCount.incrementAndGet();
        assertThat(testExecutionContext.executionStartedEvents.size()).isEqualTo(count);
        assertThat(testExecutionContext.executionFinishedEvents.size()).isEqualTo(count - 1);
    }

    @Test
    public void test3() {
        int count = testExecutionContext.executedTestCount.incrementAndGet();
        assertThat(testExecutionContext.executionStartedEvents.size()).isEqualTo(count);
        assertThat(testExecutionContext.executionFinishedEvents.size()).isEqualTo(count - 1);
    }

    private static class TestExecutionContext {

        private final AtomicInteger executedTestCount = new AtomicInteger();
        private final List<TestExecutionStartedEvent> executionStartedEvents = new ArrayList<>();
        private final List<TestExecutionFinishedEvent> executionFinishedEvents = new ArrayList<>();

        @EventListener
        public synchronized void handleTestStarted(TestExecutionStartedEvent event) {
            executionStartedEvents.add(event);
        }

        @EventListener
        public synchronized void handleTestFinished(TestExecutionFinishedEvent event) {
            executionFinishedEvents.add(event);
        }
    }
}