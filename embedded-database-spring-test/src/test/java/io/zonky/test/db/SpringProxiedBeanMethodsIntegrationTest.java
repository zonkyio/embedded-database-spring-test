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

package io.zonky.test.db;

import io.zonky.test.category.SpringTestSuite;
import io.zonky.test.support.ConditionalTestRule;
import io.zonky.test.support.TestAssumptions;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static io.zonky.test.support.MockitoAssertions.mock;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(SpringTestSuite.class)
@AutoConfigureEmbeddedDatabase(type = POSTGRES)
public class SpringProxiedBeanMethodsIntegrationTest {

    @ClassRule
    public static ConditionalTestRule conditionalTestRule = new ConditionalTestRule(TestAssumptions::assumeSpringSupportsInstanceSupplier);

    @Configuration
    static class Config {

        @Bean
        public DataSource dataSource() {
            return Mockito.mock(DataSource.class);
        }

        @Bean
        public JdbcTemplate jdbcTemplate() {
            return new JdbcTemplate(dataSource());
        }
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void checkInitializedBeans() {
        assertThat(dataSource).isNotNull().isNot(mock());
        assertThat(jdbcTemplate).isNotNull();
    }
}
