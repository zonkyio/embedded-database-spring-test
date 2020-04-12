/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
