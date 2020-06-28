package io.zonky.test.db.provider.mssql;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import io.zonky.test.category.StaticTests;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.BlockingDatabaseWrapper;
import io.zonky.test.db.provider.EmbeddedDatabase;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Category(StaticTests.class)
@RunWith(MockitoJUnitRunner.class)
public class DockerMSSQLDatabaseProviderTest {

    @Mock
    private ObjectProvider<List<MSSQLServerContainerCustomizer>> containerCustomizers;

    @Before
    public void setUp() {
        when(containerCustomizers.getIfAvailable()).thenReturn(Collections.emptyList());
    }

    @Test
    public void testGetDatabase() throws Exception {
        DockerMSSQLDatabaseProvider provider = new DockerMSSQLDatabaseProvider(new MockEnvironment(), containerCustomizers);

        DatabasePreparer preparer1 = dataSource -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("create table prime_number (number int primary key not null)");
        };

        DatabasePreparer preparer2 = dataSource -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("create table prime_number (id int primary key not null, number int not null)");
        };

        DataSource dataSource1 = provider.createDatabase(preparer1);
        DataSource dataSource2 = provider.createDatabase(preparer1);
        DataSource dataSource3 = provider.createDatabase(preparer2);

        assertThat(dataSource1).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);
        assertThat(dataSource2).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);
        assertThat(dataSource3).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);

        assertThat(getPort(dataSource1)).isEqualTo(getPort(dataSource2));
        assertThat(getPort(dataSource2)).isEqualTo(getPort(dataSource3));

        JdbcTemplate jdbcTemplate1 = new JdbcTemplate(dataSource1);
        jdbcTemplate1.update("insert into prime_number (number) values (?)", 2);
        assertThat(jdbcTemplate1.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);

        JdbcTemplate jdbcTemplate2 = new JdbcTemplate(dataSource2);
        jdbcTemplate2.update("insert into prime_number (number) values (?)", 3);
        assertThat(jdbcTemplate2.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);

        JdbcTemplate jdbcTemplate3 = new JdbcTemplate(dataSource3);
        jdbcTemplate3.update("insert into prime_number (id, number) values (?, ?)", 1, 5);
        assertThat(jdbcTemplate3.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);
    }

    @Test
    public void testContainerCustomizers() throws SQLException {
        when(containerCustomizers.getIfAvailable()).thenReturn(Collections.singletonList(container -> container.withPassword("test_Str0ng_Required_Password")));

        DatabasePreparer preparer = dataSource -> {};
        DockerMSSQLDatabaseProvider provider = new DockerMSSQLDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DataSource dataSource = provider.createDatabase(preparer);

        assertThat(dataSource.unwrap(EmbeddedDatabase.class).getUrl()).contains("password=test_Str0ng_Required_Password");
    }

    @Test
    public void providersWithSameCustomizersShouldEquals() {
        when(containerCustomizers.getIfAvailable()).thenReturn(
                Collections.singletonList(mssqlContainerCustomizer(61)),
                Collections.singletonList(mssqlContainerCustomizer(61)));

        DockerMSSQLDatabaseProvider provider1 = new DockerMSSQLDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DockerMSSQLDatabaseProvider provider2 = new DockerMSSQLDatabaseProvider(new MockEnvironment(), containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithDifferentCustomizersShouldNotEquals() {
        when(containerCustomizers.getIfAvailable()).thenReturn(
                Collections.singletonList(mssqlContainerCustomizer(60)),
                Collections.singletonList(mssqlContainerCustomizer(61)));

        DockerMSSQLDatabaseProvider provider1 = new DockerMSSQLDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DockerMSSQLDatabaseProvider provider2 = new DockerMSSQLDatabaseProvider(new MockEnvironment(), containerCustomizers);

        assertThat(provider1).isNotEqualTo(provider2);
    }

    @Test
    public void testConfigurationProperties() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zonky.test.database.mssql.docker.image", "mcr.microsoft.com/mssql/server:2017-CU20");
        environment.setProperty("zonky.test.database.mssql.client.properties.queryTimeout", "30");
        environment.setProperty("zonky.test.database.mssql.client.properties.description", "test description");
        environment.setProperty("zonky.test.database.mssql.client.properties.sendTimeAsDatetime", "false");

        DatabasePreparer preparer = dataSource -> {};
        DockerMSSQLDatabaseProvider provider = new DockerMSSQLDatabaseProvider(environment, containerCustomizers);
        DataSource dataSource = provider.createDatabase(preparer);

        assertThat(dataSource.unwrap(SQLServerDataSource.class).getQueryTimeout()).isEqualTo(30);
        assertThat(dataSource.unwrap(SQLServerDataSource.class).getDescription()).isEqualTo("test description");
        assertThat(dataSource.unwrap(SQLServerDataSource.class).getSendTimeAsDatetime()).isEqualTo(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String databaseVersion = jdbcTemplate.queryForObject("select @@version", String.class);
        assertThat(databaseVersion).startsWith("Microsoft SQL Server 2017 (RTM-CU20)");

        String maxConnections = jdbcTemplate.queryForObject("select @@max_connections", String.class);
        assertThat(maxConnections).isEqualTo("32767");
    }

    @Test
    public void providersWithDefaultConfigurationShouldEquals() {
        MockEnvironment environment = new MockEnvironment();

        DockerMSSQLDatabaseProvider provider1 = new DockerMSSQLDatabaseProvider(environment, containerCustomizers);
        DockerMSSQLDatabaseProvider provider2 = new DockerMSSQLDatabaseProvider(environment, containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithSameConfigurationShouldEquals() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zonky.test.database.mssql.docker.image", "test-image");
        environment.setProperty("zonky.test.database.mssql.client.properties.zzz", "zzz-value");

        DockerMSSQLDatabaseProvider provider1 = new DockerMSSQLDatabaseProvider(environment, containerCustomizers);
        DockerMSSQLDatabaseProvider provider2 = new DockerMSSQLDatabaseProvider(environment, containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithDifferentConfigurationShouldNotEquals() {
        Map<String, String> mockProperties = new HashMap<>();
        mockProperties.put("zonky.test.database.mssql.docker.image", "test-image");
        mockProperties.put("zonky.test.database.mssql.client.properties.zzz", "zzz-value");

        Map<String, String> diffProperties = new HashMap<>();
        diffProperties.put("zonky.test.database.mssql.docker.image", "diff-test-image");
        diffProperties.put("zonky.test.database.mssql.client.properties.zzz", "zzz-diff-value");

        for (Map.Entry<String, String> diffProperty : diffProperties.entrySet()) {
            MockEnvironment environment1 = new MockEnvironment();
            MockEnvironment environment2 = new MockEnvironment();

            for (Map.Entry<String, String> mockProperty : mockProperties.entrySet()) {
                environment1.setProperty(mockProperty.getKey(), mockProperty.getValue());
                environment2.setProperty(mockProperty.getKey(), mockProperty.getValue());
            }

            environment2.setProperty(diffProperty.getKey(), diffProperty.getValue());

            DockerMSSQLDatabaseProvider provider1 = new DockerMSSQLDatabaseProvider(environment1, containerCustomizers);
            DockerMSSQLDatabaseProvider provider2 = new DockerMSSQLDatabaseProvider(environment2, containerCustomizers);

            assertThat(provider1).isNotEqualTo(provider2);
        }
    }

    private static int getPort(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(SQLServerDataSource.class).getPortNumber();
    }

    private static MSSQLServerContainerCustomizer mssqlContainerCustomizer(long timeout) {
        return container -> container.withStartupTimeout(Duration.ofSeconds(timeout));
    }
}