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
import io.zonky.test.category.MultiFlywayTests;
import io.zonky.test.db.flyway.FlywayWrapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

@RunWith(SpringRunner.class)
@Category(MultiFlywayTests.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource1", type = POSTGRES, provider = ZONKY)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource2", type = POSTGRES, provider = DOCKER)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource3", type = POSTGRES, provider = ZONKY)
@ContextConfiguration
public class MultipleDatabasesIntegrationTest {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";

    @Configuration
    static class Config {

        @Bean(initMethod = "migrate")
        public Flyway flyway1(DataSource dataSource1) {
            FlywayWrapper wrapper = FlywayWrapper.newInstance();
            wrapper.setDataSource(dataSource1);
            wrapper.setSchemas(ImmutableList.of("test"));
            return wrapper.getFlyway();
        }

        @Bean(initMethod = "migrate")
        public Flyway flyway2(DataSource dataSource2) {
            FlywayWrapper wrapper = FlywayWrapper.newInstance();
            wrapper.setDataSource(dataSource2);
            wrapper.setSchemas(ImmutableList.of("test"));
            return wrapper.getFlyway();
        }

        @Bean(initMethod = "migrate")
        public Flyway flyway3(DataSource dataSource3) {
            FlywayWrapper wrapper = FlywayWrapper.newInstance();
            wrapper.setDataSource(dataSource3);
            wrapper.setSchemas(ImmutableList.of("test"));
            return wrapper.getFlyway();
        }

        @Bean
        public JdbcTemplate jdbcTemplate1(DataSource dataSource1) {
            return new JdbcTemplate(dataSource1);
        }

        @Bean
        public JdbcTemplate jdbcTemplate2(DataSource dataSource2) {
            return new JdbcTemplate(dataSource2);
        }

        @Bean
        public JdbcTemplate jdbcTemplate3(DataSource dataSource3) {
            return new JdbcTemplate(dataSource3);
        }
    }

    @Autowired
    private DataSource dataSource1;

    @Autowired
    private DataSource dataSource2;

    @Autowired
    private DataSource dataSource3;

    @Autowired
    private JdbcTemplate jdbcTemplate1;

    @Autowired
    private JdbcTemplate jdbcTemplate2;

    @Autowired
    private JdbcTemplate jdbcTemplate3;

    @Test
    @FlywayTest(flywayName = "flyway1", locationsForMigrate = "db/test_migration/appendable")
    @FlywayTest(flywayName = "flyway2", locationsForMigrate = "db/test_migration/dependent")
    @FlywayTest(flywayName = "flyway3", overrideLocations = true, locationsForMigrate = "db/test_migration/separated")
    public void loadDefaultMigrations() throws SQLException {
        assertThat(dataSource1).isNotNull();
        assertThat(dataSource2).isNotNull();
        assertThat(dataSource3).isNotNull();

        assertThat(getPort(dataSource1)).isEqualTo(getPort(dataSource3));
        assertThat(getPort(dataSource1)).isNotEqualTo(getPort(dataSource2));
        assertThat(getDatabaseName(dataSource1)).isNotEqualTo(getDatabaseName(dataSource3));

        assertThat(dataSource1.unwrap(PGSimpleDataSource.class).getPassword()).isNotEqualTo("docker");
        assertThat(dataSource2.unwrap(PGSimpleDataSource.class).getPassword()).isEqualTo("docker");
        assertThat(dataSource3.unwrap(PGSimpleDataSource.class).getPassword()).isNotEqualTo("docker");

        assertThat(jdbcTemplate1.queryForList(SQL_SELECT_PERSONS)).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"));

        assertThat(jdbcTemplate2.queryForList(SQL_SELECT_PERSONS)).extracting("id", "first_name", "last_name", "full_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer", "Dave Syer"),
                tuple(3L, "Will", "Smith", "Will Smith"));

        List<Map<String, Object>> persons = jdbcTemplate3.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        Map<String, Object> person = persons.get(0);
        assertThat(person).containsExactly(
                entry("id", 1L),
                entry("first_name", "Tom"),
                entry("last_name", "Hanks"));
    }

    private static int getPort(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(PGSimpleDataSource.class).getPortNumber();
    }

    private static String getDatabaseName(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(PGSimpleDataSource.class).getDatabaseName();
    }
}
