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

package io.zonky.test.db.config;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(type = POSTGRES)
@TestPropertySource(properties = {
        "zonky.test.database.postgres.client.properties.stringtype=unspecified",
        "zonky.test.database.postgres.initdb.properties.lc-collate=cs_CZ.UTF-8",
        "zonky.test.database.postgres.server.properties.max_connections=100",
        "zonky.test.database.postgres.server.properties.shared_buffers=64MB",
})
@ContextConfiguration
public class ConfigurationPropertiesIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testConfigurationProperties() throws Exception {
        assertThat(dataSource.unwrap(PGSimpleDataSource.class).getProperty("stringtype")).isEqualTo("unspecified");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String collate = jdbcTemplate.queryForObject("SELECT datcollate FROM pg_database WHERE datname NOT IN('template0', 'template1', 'postgres') LIMIT 1", String.class);
        assertThat(collate).isEqualTo("cs_CZ.UTF-8");

        String maxConnections = jdbcTemplate.queryForObject("show max_connections", String.class);
        assertThat(maxConnections).isEqualTo("100");

        String sharedBuffers = jdbcTemplate.queryForObject("show shared_buffers", String.class);
        assertThat(sharedBuffers).isEqualTo("64MB");
    }
}
