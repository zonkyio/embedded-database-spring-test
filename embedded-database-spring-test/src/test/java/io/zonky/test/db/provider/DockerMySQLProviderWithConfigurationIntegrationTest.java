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

import com.mysql.cj.jdbc.MysqlDataSource;
import io.zonky.test.category.MySQLTestSuite;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.provider.mysql.MySQLContainerCustomizer;
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

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.MYSQL;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(MySQLTestSuite.class)
@AutoConfigureEmbeddedDatabase(type = MYSQL)
@ContextConfiguration
@TestPropertySource(properties = {
        "zonky.test.database.mysql.docker.image=mysql:9.0"
})
public class DockerMySQLProviderWithConfigurationIntegrationTest {

    @Configuration
    static class Config {

        @Bean
        public MySQLContainerCustomizer mySqlContainerCustomizer() {
            return container -> container.withPassword("docker-mysql");
        }
    }

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDataSource() throws SQLException {
        assertThat(dataSource.unwrap(MysqlDataSource.class).getPassword()).isEqualTo("docker-mysql");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String databaseVersion = jdbcTemplate.queryForObject("select @@version", String.class);
        assertThat(databaseVersion).startsWith("9.0");
    }
}
