package io.zonky.test.db;

import io.zonky.test.category.MultiFlywayIntegrationTests;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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
import static org.assertj.core.api.Assertions.tuple;

@RunWith(SpringRunner.class)
@Category(MultiFlywayIntegrationTests.class)
@FlywayTest(flywayName = "flyway1")
@FlywayTest(flywayName = "flyway3", invokeCleanDB = false)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
@ContextConfiguration
public class MultipleFlywayBeansClassLevelIntegrationTest {

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
    public void databaseShouldBeLoadedByFlyway1AndAppendedByFlyway3() throws Exception {
        assertThat(dataSource).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(3);

        assertThat(persons).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"),
                tuple(3L, "Will", "Smith"));
    }
}
