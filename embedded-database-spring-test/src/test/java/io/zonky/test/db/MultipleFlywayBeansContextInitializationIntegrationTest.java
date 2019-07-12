package io.zonky.test.db;

import com.google.common.collect.ImmutableList;
import org.flywaydb.core.Flyway;
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

import io.zonky.test.category.FlywayIntegrationTests;
import static io.zonky.test.util.FlywayTestUtils.createFlyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@RunWith(SpringRunner.class)
@Category(FlywayIntegrationTests.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
@ContextConfiguration
public class MultipleFlywayBeansContextInitializationIntegrationTest {

    @Configuration
    static class Config {

        @Primary
        @DependsOn("flyway2")
        @Bean(initMethod = "migrate")
        public Flyway flyway1(DataSource dataSource) throws Exception {
            List<String> locations = ImmutableList.of("db/migration", "db/test_migration/dependent");
            return createFlyway(dataSource, "test", locations);
        }

        @Bean(initMethod = "migrate")
        public Flyway flyway2(DataSource dataSource) throws Exception {
            List<String> locations = ImmutableList.of("db/next_migration");
            return createFlyway(dataSource, "next", locations);
        }

        @Bean(initMethod = "migrate")
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
