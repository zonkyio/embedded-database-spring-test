package io.zonky.test.db;

import org.flywaydb.core.Flyway;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
@TestPropertySource(properties = {
        "flyway.url=jdbc:postgresql://localhost:5432/test",
        "flyway.user=flyway",
        "flyway.password=password",
        "flyway.schemas=test",
})
@JdbcTest
public class SpringBootFlywayPropertiesIntegrationTest {

    @Configuration
    static class Config {}

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    public void test() {
        assertThat(flyway.getDataSource()).isSameAs(dataSource);
    }
}
