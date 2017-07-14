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

package io.zonky.test.db.postgres;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
@ContextConfiguration
public class MultipleFlywayBeansIntegrationTest {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";

    @Configuration
    static class Config {

        @Bean
        public Flyway flyway1(DataSource dataSource) {
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setSchemas("test");
            flyway.setLocations("db/migration", "db/test_migration/dependent");
            return flyway;
        }

        @Bean
        public Flyway flyway2(DataSource dataSource) {
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setSchemas("test");
            flyway.setLocations("db/test_migration/separated");
            return flyway;
        }

        @Bean
        public Flyway flyway3(DataSource dataSource) {
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setSchemas("test");
            flyway.setLocations("db/test_migration/appendable");
            flyway.setValidateOnMigrate(false);
            return flyway;
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
    @FlywayTest(flywayName = "flyway1")
    public void databaseShouldBeLoadedByFlyway1() throws Exception {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        Map<String, Object> person = persons.get(0);
        assertThat(person).containsExactly(
                entry("id", 1L),
                entry("first_name", "Dave"),
                entry("last_name", "Syer"),
                entry("full_name", "Dave Syer"));
    }

    @Test
    @FlywayTest(flywayName = "flyway2")
    public void databaseShouldBeLoadedByFlyway2() throws Exception {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        Map<String, Object> person = persons.get(0);
        assertThat(person).containsExactly(
                entry("id", 1L),
                entry("first_name", "Tom"),
                entry("last_name", "Hanks"));
    }

    @Test
    @FlywayTest(flywayName = "flyway1")
    @FlywayTest(flywayName = "flyway2")
    public void databaseShouldBeOverriddenByFlyway2() throws Exception {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        Map<String, Object> person = persons.get(0);
        assertThat(person).containsExactly(
                entry("id", 1L),
                entry("first_name", "Tom"),
                entry("last_name", "Hanks"));
    }

    @Test
    @FlywayTest(flywayName = "flyway1")
    @FlywayTest(flywayName = "flyway3", invokeCleanDB = false)
    public void databaseShouldBeLoadedByFlyway1AndAppendedByFlyway3() throws Exception {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(2);

        assertThat(persons).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"));
    }
}
