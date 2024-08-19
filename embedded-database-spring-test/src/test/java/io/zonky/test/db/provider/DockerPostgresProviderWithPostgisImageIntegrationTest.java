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

package io.zonky.test.db.provider;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.provider.postgres.PostgreSQLContainerCustomizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.SQLException;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = DOCKER)
@TestPropertySource(properties = {
        "zonky.test.database.postgres.docker.image=postgis/postgis:16-3.4-alpine"
})
@ContextConfiguration
public class DockerPostgresProviderWithPostgisImageIntegrationTest {

    @Configuration
    static class Config {

        @Bean
        public PostgreSQLContainerCustomizer postgresContainerCustomizer() {
            return container -> container.withPassword("docker-postgis");
        }
    }

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDataSource() throws SQLException {
        assertThat(dataSource.unwrap(PGSimpleDataSource.class).getPassword()).isEqualTo("docker-postgis");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String postgresVersion = jdbcTemplate.queryForObject("show server_version", String.class);
        assertThat(postgresVersion).startsWith("16.");

        jdbcTemplate.update("create extension postgis");
        String postgisVersion = jdbcTemplate.queryForObject("select postgis_version()", String.class);
        assertThat(postgisVersion).startsWith("3.4");
    }
}
