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

package io.zonky.test.db.provider.mysql;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.support.BlockingDatabaseWrapper;
import io.zonky.test.db.support.TestDatabasePreparer;
import org.junit.Before;
import org.junit.Test;
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

@RunWith(MockitoJUnitRunner.class)
public class DockerMySQLDatabaseProviderTest {

    @Mock
    private ObjectProvider<List<MySQLContainerCustomizer>> containerCustomizers;

    @Before
    public void setUp() {
        when(containerCustomizers.getIfAvailable()).thenReturn(Collections.emptyList());
    }

    @Test
    public void testGetDatabase() throws Exception {
        DockerMySQLDatabaseProvider provider = new DockerMySQLDatabaseProvider(new MockEnvironment(), containerCustomizers);

        DatabasePreparer preparer1 = TestDatabasePreparer.of(dataSource -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("create table prime_number (number int primary key not null)");
        });

        DatabasePreparer preparer2 = TestDatabasePreparer.of(dataSource -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("create table prime_number (id int primary key not null, number int not null)");
        });

        DataSource dataSource1 = provider.createDatabase(preparer1);
        DataSource dataSource2 = provider.createDatabase(preparer1);
        DataSource dataSource3 = provider.createDatabase(preparer2);

        assertThat(dataSource1).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);
        assertThat(dataSource2).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);
        assertThat(dataSource3).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);

        assertThat(getPort(dataSource1)).isNotEqualTo(getPort(dataSource2));
        assertThat(getPort(dataSource2)).isNotEqualTo(getPort(dataSource3));

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
    public void testDatabaseRecycling() throws SQLException {
        DockerMySQLDatabaseProvider provider = new DockerMySQLDatabaseProvider(new MockEnvironment(), containerCustomizers);

        DatabasePreparer preparer1 = TestDatabasePreparer.of(dataSource -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("create table prime_number (number int primary key not null)");
        });

        DatabasePreparer preparer2 = TestDatabasePreparer.of(dataSource -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("create table prime_number (id int primary key not null, number int not null)");
        });

        EmbeddedDatabase dataSource1 = provider.createDatabase(preparer1);
        JdbcTemplate jdbcTemplate1 = new JdbcTemplate(dataSource1);
        jdbcTemplate1.update("insert into prime_number (number) values (?)", 2);
        assertThat(jdbcTemplate1.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);
        dataSource1.close();

        EmbeddedDatabase dataSource2 = provider.createDatabase(preparer1);
        JdbcTemplate jdbcTemplate2 = new JdbcTemplate(dataSource2);
        jdbcTemplate2.update("insert into prime_number (number) values (?)", 3);
        assertThat(jdbcTemplate2.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);
        dataSource2.close();

        EmbeddedDatabase dataSource3 = provider.createDatabase(preparer2);
        JdbcTemplate jdbcTemplate3 = new JdbcTemplate(dataSource3);
        jdbcTemplate3.update("insert into prime_number (id, number) values (?, ?)", 1, 5);
        assertThat(jdbcTemplate3.queryForObject("select count(*) from prime_number", Integer.class)).isEqualTo(1);
        dataSource3.close();

        assertThat(dataSource1).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);
        assertThat(dataSource2).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);
        assertThat(dataSource3).isNotNull().isExactlyInstanceOf(BlockingDatabaseWrapper.class);

        assertThat(getPort(dataSource1)).isEqualTo(getPort(dataSource2));
        assertThat(getPort(dataSource2)).isEqualTo(getPort(dataSource3));
    }

    @Test
    public void testContainerCustomizers() throws SQLException {
        when(containerCustomizers.getIfAvailable()).thenReturn(Collections.singletonList(container -> container.withPassword("test")));

        DatabasePreparer preparer = TestDatabasePreparer.empty();
        DockerMySQLDatabaseProvider provider = new DockerMySQLDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DataSource dataSource = provider.createDatabase(preparer);

        assertThat(dataSource.unwrap(MysqlDataSource.class).getPassword()).isEqualTo("test");
    }

    @Test
    public void providersWithSameCustomizersShouldEquals() {
        when(containerCustomizers.getIfAvailable()).thenReturn(
                Collections.singletonList(mysqlContainerCustomizer(61)),
                Collections.singletonList(mysqlContainerCustomizer(61)));

        DockerMySQLDatabaseProvider provider1 = new DockerMySQLDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DockerMySQLDatabaseProvider provider2 = new DockerMySQLDatabaseProvider(new MockEnvironment(), containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithDifferentCustomizersShouldNotEquals() {
        when(containerCustomizers.getIfAvailable()).thenReturn(
                Collections.singletonList(mysqlContainerCustomizer(60)),
                Collections.singletonList(mysqlContainerCustomizer(61)));

        DockerMySQLDatabaseProvider provider1 = new DockerMySQLDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DockerMySQLDatabaseProvider provider2 = new DockerMySQLDatabaseProvider(new MockEnvironment(), containerCustomizers);

        assertThat(provider1).isNotEqualTo(provider2);
    }

    @Test
    public void testConfigurationProperties() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zonky.test.database.mysql.docker.image", "mysql:5.6.48");
        environment.setProperty("zonky.test.database.mysql.client.properties.socketTimeout", "30");
        environment.setProperty("zonky.test.database.mysql.client.properties.description", "test description");
        environment.setProperty("zonky.test.database.mysql.client.properties.autoReconnect", "true");

        DatabasePreparer preparer = TestDatabasePreparer.empty();
        DockerMySQLDatabaseProvider provider = new DockerMySQLDatabaseProvider(environment, containerCustomizers);
        DataSource dataSource = provider.createDatabase(preparer);

        assertThat(dataSource.unwrap(MysqlDataSource.class).getSocketTimeout()).isEqualTo(30);
        assertThat(dataSource.unwrap(MysqlDataSource.class).getDescription()).isEqualTo("test description");
        assertThat(dataSource.unwrap(MysqlDataSource.class).getAutoReconnect()).isEqualTo(true);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String databaseVersion = jdbcTemplate.queryForObject("select @@version", String.class);
        assertThat(databaseVersion).startsWith("5.6.48");

        String maxConnections = jdbcTemplate.queryForObject("select @@max_connections", String.class);
        assertThat(maxConnections).isEqualTo("151");
    }

    @Test
    public void providersWithDefaultConfigurationShouldEquals() {
        MockEnvironment environment = new MockEnvironment();

        DockerMySQLDatabaseProvider provider1 = new DockerMySQLDatabaseProvider(environment, containerCustomizers);
        DockerMySQLDatabaseProvider provider2 = new DockerMySQLDatabaseProvider(environment, containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithSameConfigurationShouldEquals() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zonky.test.database.mysql.docker.image", "test-image");
        environment.setProperty("zonky.test.database.mysql.client.properties.zzz", "zzz-value");

        DockerMySQLDatabaseProvider provider1 = new DockerMySQLDatabaseProvider(environment, containerCustomizers);
        DockerMySQLDatabaseProvider provider2 = new DockerMySQLDatabaseProvider(environment, containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithDifferentConfigurationShouldNotEquals() {
        Map<String, String> mockProperties = new HashMap<>();
        mockProperties.put("zonky.test.database.mysql.docker.image", "test-image");
        mockProperties.put("zonky.test.database.mysql.client.properties.zzz", "zzz-value");

        Map<String, String> diffProperties = new HashMap<>();
        diffProperties.put("zonky.test.database.mysql.docker.image", "diff-test-image");
        diffProperties.put("zonky.test.database.mysql.client.properties.zzz", "zzz-diff-value");

        for (Map.Entry<String, String> diffProperty : diffProperties.entrySet()) {
            MockEnvironment environment1 = new MockEnvironment();
            MockEnvironment environment2 = new MockEnvironment();

            for (Map.Entry<String, String> mockProperty : mockProperties.entrySet()) {
                environment1.setProperty(mockProperty.getKey(), mockProperty.getValue());
                environment2.setProperty(mockProperty.getKey(), mockProperty.getValue());
            }

            environment2.setProperty(diffProperty.getKey(), diffProperty.getValue());

            DockerMySQLDatabaseProvider provider1 = new DockerMySQLDatabaseProvider(environment1, containerCustomizers);
            DockerMySQLDatabaseProvider provider2 = new DockerMySQLDatabaseProvider(environment2, containerCustomizers);

            assertThat(provider1).isNotEqualTo(provider2);
        }
    }

    private static int getPort(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(MysqlDataSource.class).getPortNumber();
    }

    private static MySQLContainerCustomizer mysqlContainerCustomizer(long timeout) {
        return container -> container.withStartupTimeout(Duration.ofSeconds(timeout));
    }
}