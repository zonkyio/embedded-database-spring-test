/*
 * Copyright 2021 the original author or authors.
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
import liquibase.integration.spring.SpringLiquibase;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

@Category(LiquibaseTestSuite.class)
public class LiquibaseDatabasePreparerTest {

    private LiquibaseDatabasePreparer preparer;

    @Before
    public void setUp() throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        LiquibaseDescriptor descriptor = LiquibaseDescriptor.from(liquibase);

        preparer = new LiquibaseDatabasePreparer(descriptor);
    }

    @Test
    public void estimatedDuration() {
        assertThat(preparer.estimatedDuration()).isEqualTo(214);
    }
}