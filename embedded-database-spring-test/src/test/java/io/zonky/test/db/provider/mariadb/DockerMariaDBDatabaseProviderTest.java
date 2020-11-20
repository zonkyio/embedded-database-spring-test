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

package io.zonky.test.db.provider.mariadb;

import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.support.BlockingDatabaseWrapper;
import io.zonky.test.db.support.TestDatabasePreparer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mariadb.jdbc.MariaDbDataSource;
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
public class DockerMariaDBDatabaseProviderTest {

    @Mock
    private ObjectProvider<List<MariaDBContainerCustomizer>> containerCustomizers;

    @Before
    public void setUp() {
        when(containerCustomizers.getIfAvailable()).thenReturn(Collections.emptyList());
    }

    @Test
    public void testGetDatabase() throws Exception {
        DockerMariaDBDatabaseProvider provider = new DockerMariaDBDatabaseProvider(new MockEnvironment(), containerCustomizers);

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
        DockerMariaDBDatabaseProvider provider = new DockerMariaDBDatabaseProvider(new MockEnvironment(), containerCustomizers);

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
        DockerMariaDBDatabaseProvider provider = new DockerMariaDBDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DataSource dataSource = provider.createDatabase(preparer);

        assertThat(dataSource.unwrap(EmbeddedDatabase.class).getUrl()).contains("password=test");
    }

    @Test
    public void providersWithSameCustomizersShouldEquals() {
        when(containerCustomizers.getIfAvailable()).thenReturn(
                Collections.singletonList(mariadbContainerCustomizer(61)),
                Collections.singletonList(mariadbContainerCustomizer(61)));

        DockerMariaDBDatabaseProvider provider1 = new DockerMariaDBDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DockerMariaDBDatabaseProvider provider2 = new DockerMariaDBDatabaseProvider(new MockEnvironment(), containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithDifferentCustomizersShouldNotEquals() {
        when(containerCustomizers.getIfAvailable()).thenReturn(
                Collections.singletonList(mariadbContainerCustomizer(60)),
                Collections.singletonList(mariadbContainerCustomizer(61)));

        DockerMariaDBDatabaseProvider provider1 = new DockerMariaDBDatabaseProvider(new MockEnvironment(), containerCustomizers);
        DockerMariaDBDatabaseProvider provider2 = new DockerMariaDBDatabaseProvider(new MockEnvironment(), containerCustomizers);

        assertThat(provider1).isNotEqualTo(provider2);
    }

    @Test
    public void testConfigurationProperties() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zonky.test.database.mariadb.docker.image", "mariadb:10.1");
        environment.setProperty("zonky.test.database.mariadb.client.properties.loginTimeout", "60");

        DatabasePreparer preparer = TestDatabasePreparer.empty();
        DockerMariaDBDatabaseProvider provider = new DockerMariaDBDatabaseProvider(environment, containerCustomizers);
        DataSource dataSource = provider.createDatabase(preparer);

        assertThat(dataSource.unwrap(MariaDbDataSource.class).getLoginTimeout()).isEqualTo(60);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String databaseVersion = jdbcTemplate.queryForObject("select @@version", String.class);
        assertThat(databaseVersion).startsWith("10.1");

        String maxConnections = jdbcTemplate.queryForObject("select @@max_connections", String.class);
        assertThat(maxConnections).isEqualTo("100");
    }

    @Test
    public void providersWithDefaultConfigurationShouldEquals() {
        MockEnvironment environment = new MockEnvironment();

        DockerMariaDBDatabaseProvider provider1 = new DockerMariaDBDatabaseProvider(environment, containerCustomizers);
        DockerMariaDBDatabaseProvider provider2 = new DockerMariaDBDatabaseProvider(environment, containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithSameConfigurationShouldEquals() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zonky.test.database.mariadb.docker.image", "test-image");
        environment.setProperty("zonky.test.database.mariadb.client.properties.zzz", "zzz-value");

        DockerMariaDBDatabaseProvider provider1 = new DockerMariaDBDatabaseProvider(environment, containerCustomizers);
        DockerMariaDBDatabaseProvider provider2 = new DockerMariaDBDatabaseProvider(environment, containerCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithDifferentConfigurationShouldNotEquals() {
        Map<String, String> mockProperties = new HashMap<>();
        mockProperties.put("zonky.test.database.mariadb.docker.image", "test-image");
        mockProperties.put("zonky.test.database.mariadb.client.properties.zzz", "zzz-value");

        Map<String, String> diffProperties = new HashMap<>();
        diffProperties.put("zonky.test.database.mariadb.docker.image", "diff-test-image");
        diffProperties.put("zonky.test.database.mariadb.client.properties.zzz", "zzz-diff-value");

        for (Map.Entry<String, String> diffProperty : diffProperties.entrySet()) {
            MockEnvironment environment1 = new MockEnvironment();
            MockEnvironment environment2 = new MockEnvironment();

            for (Map.Entry<String, String> mockProperty : mockProperties.entrySet()) {
                environment1.setProperty(mockProperty.getKey(), mockProperty.getValue());
                environment2.setProperty(mockProperty.getKey(), mockProperty.getValue());
            }

            environment2.setProperty(diffProperty.getKey(), diffProperty.getValue());

            DockerMariaDBDatabaseProvider provider1 = new DockerMariaDBDatabaseProvider(environment1, containerCustomizers);
            DockerMariaDBDatabaseProvider provider2 = new DockerMariaDBDatabaseProvider(environment2, containerCustomizers);

            assertThat(provider1).isNotEqualTo(provider2);
        }
    }

    private static int getPort(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(MariaDbDataSource.class).getPortNumber();
    }

    private static MariaDBContainerCustomizer mariadbContainerCustomizer(long timeout) {
        return container -> container.withStartupTimeout(Duration.ofSeconds(timeout));
    }
}