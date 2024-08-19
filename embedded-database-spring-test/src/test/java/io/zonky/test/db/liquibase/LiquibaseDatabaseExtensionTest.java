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

package io.zonky.test.db.liquibase;

import io.zonky.test.category.LiquibaseTestSuite;
import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.context.DatabaseTargetSource;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.runners.util.TestMethodsFinder;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.aop.framework.Advised;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(MockitoJUnitRunner.class)
@Category(LiquibaseTestSuite.class)
public class LiquibaseDatabaseExtensionTest {

    @Mock
    private DatabaseContext databaseContext;

    private SpringLiquibase liquibase;

    @BeforeClass
    public static void beforeClass() throws ClassNotFoundException {
        System.out.println("TestMethodsFinder.hasTestMethods(SpringLiquibase.class)...");
        boolean hasTestMethods = TestMethodsFinder.hasTestMethods(SpringLiquibase.class);
        System.out.println("TestMethodsFinder.hasTestMethods(SpringLiquibase.class): " + hasTestMethods);
        System.out.println("TestMethodsFinder.hasTestMethods(LiquibaseException.class)...");
        hasTestMethods = TestMethodsFinder.hasTestMethods(LiquibaseException.class);
        System.out.println("TestMethodsFinder.hasTestMethods(LiquibaseException.class): " + hasTestMethods);
        Class<?> liquibaseException = LiquibaseDatabaseExtensionTest.class.getClassLoader().loadClass("liquibase.exception.LiquibaseException");
    }

    @Before
    public void setUp() throws ClassNotFoundException {
        Advised dataSource = mock(Advised.class, withSettings().extraInterfaces(DataSource.class));
        when(dataSource.getTargetSource()).thenReturn(new DatabaseTargetSource(databaseContext));

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource((DataSource) dataSource);

        LiquibaseDatabaseExtension extension = new LiquibaseDatabaseExtension();
        this.liquibase = (SpringLiquibase) extension.postProcessBeforeInitialization(liquibase, "liquibase");
    }

    @Test
    public void testMigrate() throws LiquibaseException {
        liquibase.afterPropertiesSet();

        verify(databaseContext).apply(new LiquibaseDatabasePreparer(LiquibaseDescriptor.from(liquibase)));
    }
}
