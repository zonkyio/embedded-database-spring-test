package io.zonky.test.db;

import com.google.common.collect.ImmutableList;
import io.zonky.test.category.FlywayTestSuite;
import io.zonky.test.db.flyway.FlywayWrapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.annotation.Timed;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(FlywayTestSuite.class)
@AutoConfigureEmbeddedDatabase(type = POSTGRES)
@TestPropertySource(properties = "zonky.test.database.postgres.server.properties.max_connections=11")
@ContextConfiguration
public class ConnectionLeakIntegrationTest {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";

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
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Repeat(30)
    @Timed(millis = 30000)
    @FlywayTest(locationsForMigrate = "db/test_migration/appendable")
    public void testEmbeddedDatabase() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(2);
    }
}
