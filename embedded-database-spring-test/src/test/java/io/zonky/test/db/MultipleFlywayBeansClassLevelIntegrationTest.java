/*
 * Copyright 2016 the original author or authors.
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
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import io.zonky.test.category.MultiFlywayIntegrationTests;
import static io.zonky.test.util.FlywayTestUtils.createFlyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@RunWith(SpringRunner.class)
@Category(MultiFlywayIntegrationTests.class)
@FlywayTest(flywayName = "flyway1")
@FlywayTest(flywayName = "flyway2")
@FlywayTest(flywayName = "flyway3", invokeCleanDB = false)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
@ContextConfiguration
public class MultipleFlywayBeansClassLevelIntegrationTest {

    @Configuration
    static class Config {

        @Primary
        @DependsOn("flyway2")
        @Bean
        public Flyway flyway1(DataSource dataSource) throws Exception {
            List<String> locations = ImmutableList.of("db/migration", "db/test_migration/dependent");
            return createFlyway(dataSource, "test", locations);
        }

        @Bean
        public Flyway flyway2(DataSource dataSource) throws Exception {
            List<String> locations = ImmutableList.of("db/next_migration");
            return createFlyway(dataSource, "next", locations);
        }

        @Bean
        public Flyway flyway3(DataSource dataSource) throws Exception {
            List<String> locations = ImmutableList.of("db/test_migration/appendable");
            return createFlyway(dataSource, "test", locations, false);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void databaseShouldBeLoadedByFlyway1AndAppendedByFlyway3() {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList("select * from test.person");
        assertThat(persons).isNotNull().hasSize(3);

        assertThat(persons).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"),
                tuple(3L, "Will", "Smith"));

        List<Map<String, Object>> nextPersons = jdbcTemplate.queryForList("select * from next.person");
        assertThat(nextPersons).isNotNull().hasSize(1);

        assertThat(nextPersons).extracting("id", "first_name", "surname").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"));
    }
}
