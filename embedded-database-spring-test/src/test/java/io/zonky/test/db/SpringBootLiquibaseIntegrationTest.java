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

package io.zonky.test.db;

import io.zonky.test.category.LiquibaseTestSuite;
import io.zonky.test.support.ConditionalTestRule;
import io.zonky.test.support.TestAssumptions;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@RunWith(SpringRunner.class)
@Category(LiquibaseTestSuite.class)
@AutoConfigureEmbeddedDatabase(type = POSTGRES)
@TestPropertySource(properties = {
        "flyway.enabled=false",
        "spring.flyway.enabled=false",
})
@JdbcTest
public class SpringBootLiquibaseIntegrationTest {

    @ClassRule
    public static ConditionalTestRule conditionalTestRule = new ConditionalTestRule(TestAssumptions::assumeSpringBootSupportsJdbcTestAnnotation);

    private static final String SQL_SELECT_PERSONS = "select * from test.person";

    @Configuration
    static class Config {

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testJdbcTemplate() {
        assertThat(jdbcTemplate).isNotNull();

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        Map<String, Object> person = persons.get(0);
        assertThat(person).containsExactly(
                entry("id", 1L),
                entry("first_name", "Dave"),
                entry("last_name", "Syer"));
    }
}
