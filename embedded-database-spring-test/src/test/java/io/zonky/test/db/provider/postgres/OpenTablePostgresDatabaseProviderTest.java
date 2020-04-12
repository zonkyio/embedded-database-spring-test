/*
 * Copyright 2019 the original author or authors.
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

package io.zonky.test.db.provider.postgres;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import io.zonky.test.db.preparer.DatabasePreparer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.SocketUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OpenTablePostgresDatabaseProviderTest {

    @Mock
    private ObjectProvider<List<Consumer<EmbeddedPostgres.Builder>>> databaseCustomizers;

    @Before
    public void setUp() {
        when(databaseCustomizers.getIfAvailable()).thenReturn(Collections.emptyList());
    }

    @Test
    public void testGetDatabase() throws Exception {
        OpenTablePostgresDatabaseProvider provider = new OpenTablePostgresDatabaseProvider(new MockEnvironment(), databaseCustomizers);

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
    public void testDatabaseCustomizers() throws Exception {
        int randomPort = SocketUtils.findAvailableTcpPort();
        when(databaseCustomizers.getIfAvailable()).thenReturn(Collections.singletonList(builder -> builder.setPort(randomPort)));

        DatabasePreparer preparer = dataSource -> {};
        OpenTablePostgresDatabaseProvider provider = new OpenTablePostgresDatabaseProvider(new MockEnvironment(), databaseCustomizers);
        DataSource dataSource = provider.createDatabase(preparer);

        assertThat(dataSource.unwrap(PGSimpleDataSource.class).getPortNumber()).isEqualTo(randomPort);
    }

    @Test
    public void testConfigurationProperties() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zonky.test.database.postgres.client.properties.stringtype", "unspecified");
        environment.setProperty("zonky.test.database.postgres.initdb.properties.lc-collate", "cs_CZ.UTF-8");
        environment.setProperty("zonky.test.database.postgres.server.properties.max_connections", "100");
        environment.setProperty("zonky.test.database.postgres.server.properties.shared_buffers", "64MB");

        DatabasePreparer preparer = dataSource -> {};
        OpenTablePostgresDatabaseProvider provider = new OpenTablePostgresDatabaseProvider(environment, databaseCustomizers);
        DataSource dataSource = provider.createDatabase(preparer);

        assertThat(dataSource.unwrap(PGSimpleDataSource.class).getProperty("stringtype")).isEqualTo("unspecified");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String collate = jdbcTemplate.queryForObject("show lc_collate", String.class);
        assertThat(collate).isEqualTo("cs_CZ.UTF-8");

        String maxConnections = jdbcTemplate.queryForObject("show max_connections", String.class);
        assertThat(maxConnections).isEqualTo("300");

        String sharedBuffers = jdbcTemplate.queryForObject("show shared_buffers", String.class);
        assertThat(sharedBuffers).isEqualTo("64MB");
    }

    @Test
    public void providersWithDefaultConfigurationShouldEquals() {
        MockEnvironment environment = new MockEnvironment();

        OpenTablePostgresDatabaseProvider provider1 = new OpenTablePostgresDatabaseProvider(environment, databaseCustomizers);
        OpenTablePostgresDatabaseProvider provider2 = new OpenTablePostgresDatabaseProvider(environment, databaseCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithSameConfigurationShouldEquals() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zonky.test.database.postgres.initdb.properties.xxx", "xxx-value");
        environment.setProperty("zonky.test.database.postgres.server.properties.yyy", "yyy-value");
        environment.setProperty("zonky.test.database.postgres.client.properties.zzz", "zzz-value");

        OpenTablePostgresDatabaseProvider provider1 = new OpenTablePostgresDatabaseProvider(environment, databaseCustomizers);
        OpenTablePostgresDatabaseProvider provider2 = new OpenTablePostgresDatabaseProvider(environment, databaseCustomizers);

        assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    public void providersWithDifferentConfigurationShouldNotEquals() {
        Map<String, String> mockProperties = new HashMap<>();
        mockProperties.put("zonky.test.database.postgres.initdb.properties.xxx", "xxx-value");
        mockProperties.put("zonky.test.database.postgres.server.properties.yyy", "yyy-value");
        mockProperties.put("zonky.test.database.postgres.client.properties.zzz", "zzz-value");

        Map<String, String> diffProperties = new HashMap<>();
        diffProperties.put("zonky.test.database.postgres.initdb.properties.xxx", "xxx-diff-value");
        diffProperties.put("zonky.test.database.postgres.server.properties.yyy", "yyy-diff-value");
        diffProperties.put("zonky.test.database.postgres.client.properties.zzz", "zzz-diff-value");

        for (Map.Entry<String, String> diffProperty : diffProperties.entrySet()) {
            MockEnvironment environment1 = new MockEnvironment();
            MockEnvironment environment2 = new MockEnvironment();

            for (Map.Entry<String, String> mockProperty : mockProperties.entrySet()) {
                environment1.setProperty(mockProperty.getKey(), mockProperty.getValue());
                environment2.setProperty(mockProperty.getKey(), mockProperty.getValue());
            }

            environment2.setProperty(diffProperty.getKey(), diffProperty.getValue());

            OpenTablePostgresDatabaseProvider provider1 = new OpenTablePostgresDatabaseProvider(environment1, databaseCustomizers);
            OpenTablePostgresDatabaseProvider provider2 = new OpenTablePostgresDatabaseProvider(environment2, databaseCustomizers);

            assertThat(provider1).isNotEqualTo(provider2);
        }
    }

    private static int getPort(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(PGSimpleDataSource.class).getPortNumber();
    }
}