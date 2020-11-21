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

import com.google.common.collect.ImmutableList;
import io.zonky.test.category.FlywayTestSuite;
import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.flyway.FlywayWrapper;
import io.zonky.test.db.flyway.preparer.MigrateFlywayDatabasePreparer;
import io.zonky.test.support.ConditionalTestRule;
import io.zonky.test.support.SpyPostProcessor;
import io.zonky.test.support.TestAssumptions;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;

import javax.sql.DataSource;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static io.zonky.test.db.context.DatabaseContext.ContextState.DIRTY;
import static io.zonky.test.db.context.DatabaseContext.ContextState.FRESH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

@RunWith(SpringRunner.class)
@Category(FlywayTestSuite.class)
@AutoConfigureEmbeddedDatabase(type = POSTGRES)
@TestPropertySource(properties = {
        "liquibase.enabled=false",
        "spring.liquibase.enabled=false"
})
@JdbcTest
public class FlywayTransactionalIntegrationTest {

    @ClassRule
    public static ConditionalTestRule conditionalTestRule = new ConditionalTestRule(TestAssumptions::assumeSpringBootSupportsJdbcTestAnnotation);

    @Configuration
    static class Config {

        @Bean(initMethod = "migrate")
        public Flyway flyway(DataSource dataSource) {
            FlywayWrapper wrapper = FlywayWrapper.newInstance();
            wrapper.setDataSource(dataSource);
            wrapper.setSchemas(ImmutableList.of("test"));
            return wrapper.getFlyway();
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public BeanPostProcessor spyPostProcessor() {
            return new SpyPostProcessor((bean, beanName) -> bean instanceof DatabaseContext);
        }
    }

    @Autowired
    private DatabaseContext databaseContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeTransaction
    public void beforeTransaction() {
        assertThat(databaseContext.getState()).isEqualTo(FRESH);
    }

    @Before
    public void beforeTestExecution() {
        assertThat(databaseContext.getState()).isEqualTo(DIRTY);
    }

    @AfterTransaction
    public void afterTransaction() throws Exception {
        assertThat(databaseContext.getState()).isEqualTo(DIRTY);

        InOrder inOrder = inOrder(databaseContext);

        inOrder.verify(databaseContext).apply(any(MigrateFlywayDatabasePreparer.class));
        inOrder.verify(databaseContext).reset();
        inOrder.verify(databaseContext, times(3)).getState();
        inOrder.verify(databaseContext, times(1)).getDatabase();
        inOrder.verify(databaseContext, times(2)).getState();
    }

    @Test
    @FlywayTest
    public void test() {
        assertThat(databaseContext).isNotNull();
        assertThat(jdbcTemplate.queryForList("select * from test.person")).hasSize(1);
    }
}
