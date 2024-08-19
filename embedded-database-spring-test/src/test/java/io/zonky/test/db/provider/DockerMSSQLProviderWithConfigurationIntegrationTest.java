package io.zonky.test.db.provider;

import io.zonky.test.category.MSSQLTestSuite;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.provider.mssql.MSSQLServerContainerCustomizer;
import io.zonky.test.db.provider.mssql.MsSQLEmbeddedDatabase;
import io.zonky.test.support.ConditionalTestRule;
import io.zonky.test.support.TestAssumptions;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.SQLException;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.MSSQL;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(MSSQLTestSuite.class)
@AutoConfigureEmbeddedDatabase(type = MSSQL)
@TestPropertySource(properties = {
        "zonky.test.database.mssql.docker.image=mcr.microsoft.com/mssql/server:2022-CU14"
})
@ContextConfiguration
public class DockerMSSQLProviderWithConfigurationIntegrationTest {

    @ClassRule
    public static ConditionalTestRule conditionalTestRule = new ConditionalTestRule(TestAssumptions::assumeLicenceAcceptance);

    @Configuration
    static class Config {

        @Bean
        public MSSQLServerContainerCustomizer mssqlContainerCustomizer() {
            return container -> container.withPassword("docker_Str0ng_Required_Password");
        }
    }

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDataSource() throws SQLException {
        assertThat(dataSource.unwrap(MsSQLEmbeddedDatabase.class).getJdbcUrl()).contains("password=docker_Str0ng_Required_Password");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String databaseVersion = jdbcTemplate.queryForObject("select @@version", String.class);
        assertThat(databaseVersion).startsWith("Microsoft SQL Server 2017 (RTM-CU20)");
    }
}
