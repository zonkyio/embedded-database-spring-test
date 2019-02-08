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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.jdbc.JdbcTestUtils;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
public class EmptyDatabaseIntegrationTest {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.afterPropertiesSet();
    }

    @After
    public void tearDown() {
        JdbcTestUtils.dropTables(jdbcTemplate, "test.person");
    }

    @Test
    @Sql(statements = "create schema if not exists test")
    @Sql(scripts = {
            "/db/migration/V0001_1__create_person_table.sql",
            "/db/migration/V0002_1__rename_surname_column.sql"
    })
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
    @Sql(statements = "create schema if not exists test")
    @Sql(scripts = {
            "/db/migration/V0001_1__create_person_table.sql",
            "/db/migration/V0002_1__rename_surname_column.sql",
            "/db/test_migration/appendable/V1000_1__create_test_data.sql"
    })
    public void loadAppendableTestMigrations() {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(2);

        assertThat(persons).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"));
    }

    @Test
    @Sql(statements = "create schema if not exists test")
    @Sql(scripts = {
            "/db/migration/V0001_1__create_person_table.sql",
            "/db/test_migration/dependent/V0001_2__add_full_name_column.sql",
            "/db/migration/V0002_1__rename_surname_column.sql"
    })
    public void loadDependentTestMigrations() {
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
    @Sql(statements = "create schema if not exists test")
    @Sql(scripts = {
            "/db/test_migration/separated/V1000_1__create_test_person_table.sql"
    })
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
