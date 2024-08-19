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
import io.zonky.test.db.flyway.FlywayClassUtils;
import io.zonky.test.db.flyway.FlywayVersion;
import io.zonky.test.db.flyway.FlywayWrapper;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.support.SpyPostProcessor;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

@RunWith(SpringRunner.class)
@Category(FlywayTestSuite.class)
@TestExecutionListeners(
        mergeMode = MERGE_WITH_DEFAULTS,
        listeners = FlywayMigrationIntegrationTest.class
)
@AutoConfigureEmbeddedDatabase(type = POSTGRES)
@ContextConfiguration
public class FlywayMigrationIntegrationTest extends AbstractTestExecutionListener {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";

    private static final FlywayVersion flywayVersion = FlywayClassUtils.getFlywayVersion();

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
            return new SpyPostProcessor((bean, beanName) ->
                    bean instanceof DatabaseContext || beanName.equals("dockerPostgresDatabaseProvider"));
        }
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterTestClass(TestContext testContext) throws ClassNotFoundException {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        DatabaseContext databaseContext = applicationContext.getBean(DatabaseContext.class);
        DatabaseProvider databaseProvider = applicationContext.getBean("dockerPostgresDatabaseProvider", DatabaseProvider.class);

        verify(databaseContext, times(4)).reset();
        verify(databaseContext, times(6)).apply(any());

        // the additional call is caused by detecting the database type in ClassicConfiguration#setDataSource
        // affected flyway versions: 9.20 - 10.1
        if (flywayVersion.isLessThan("9.20") || flywayVersion.isGreaterThan("10.1")) {
            verify(databaseProvider, times(5)).createDatabase(any());
        } else {
            verify(databaseProvider, times(6)).createDatabase(any());
        }

        Mockito.reset(databaseContext, databaseProvider);
    }

    @Test
    @FlywayTest
    public void loadDefaultMigrations() {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        Map<String, Object> person = persons.get(0);
        assertThat(person).containsExactly(
                entry("id", 1L),
                entry("first_name", "Dave"),
                entry("last_name", "Syer"));
    }

    @Test
    @FlywayTest(locationsForMigrate = "db/test_migration/appendable")
    public void loadAppendableTestMigrations() {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(2);

        assertThat(persons).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"));
    }

    @Test
    @FlywayTest(locationsForMigrate = "db/test_migration/dependent")
    public void loadDependentTestMigrations() {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(2);

        assertThat(persons).extracting("id", "first_name", "last_name", "full_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer", "Dave Syer"),
                tuple(3L, "Will", "Smith", "Will Smith"));
    }

    @Test
    @FlywayTest(overrideLocations = true, locationsForMigrate = "db/test_migration/separated")
    public void loadIndependentTestMigrations() {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        Map<String, Object> person = persons.get(0);
        assertThat(person).containsExactly(
                entry("id", 1L),
                entry("first_name", "Tom"),
                entry("last_name", "Hanks"));
    }
}
