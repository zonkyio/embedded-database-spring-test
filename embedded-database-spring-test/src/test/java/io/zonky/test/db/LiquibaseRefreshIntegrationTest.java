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

import io.zonky.test.category.LiquibaseTestSuite;
import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.support.SpyPostProcessor;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

@RunWith(SpringRunner.class)
@Category(LiquibaseTestSuite.class)
@TestExecutionListeners(
        mergeMode = MERGE_WITH_DEFAULTS,
        listeners = LiquibaseRefreshIntegrationTest.class
)
@AutoConfigureEmbeddedDatabase(type = POSTGRES, refresh = AFTER_EACH_TEST_METHOD)
@ContextConfiguration
public class LiquibaseRefreshIntegrationTest extends AbstractTestExecutionListener {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";
    private static final String SQL_INSERT_PERSON = "insert into test.person (id, first_name, last_name) values (?, ?, ?);";

    @Configuration
    static class Config {

        @Bean
        public SpringLiquibase liquibase(DataSource dataSource) {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
            return liquibase;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public BeanPostProcessor spyPostProcessor() {
            return new SpyPostProcessor((bean, beanName) ->
                    bean instanceof DatabaseContext || beanName.equals("dockerPostgresDatabaseProvider"));
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        DatabaseContext databaseContext = applicationContext.getBean(DatabaseContext.class);
        DatabaseProvider databaseProvider = applicationContext.getBean("dockerPostgresDatabaseProvider", DatabaseProvider.class);

        verify(databaseContext, atLeast(2)).reset();
        verify(databaseContext, times(1)).apply(any());
        verify(databaseProvider, atLeast(1)).createDatabase(any());

        Mockito.reset(databaseContext, databaseProvider);
    }

    @Test
    public void isolatedTest1() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        jdbcTemplate.update(SQL_INSERT_PERSON, 2, "Tom", "Hanks");
    }

    @Test
    public void isolatedTest2() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        jdbcTemplate.update(SQL_INSERT_PERSON, 2, "Will", "Smith");
    }

    @Test
    public void isolatedTest3() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        jdbcTemplate.update(SQL_INSERT_PERSON, 2, "Eddie", "Murphy");
    }
}
