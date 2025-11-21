/*
 * Copyright 2026 the original author or authors.
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

package io.zonky.test.db.sb4;

import io.zonky.test.category.LiquibaseTestSuite;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.support.ConditionalTestRule;
import io.zonky.test.support.TestAssumptions;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(LiquibaseTestSuite.class)
@AutoConfigureEmbeddedDatabase(type = POSTGRES)
@TestPropertySource(properties = {
        "flyway.enabled=false",
        "spring.flyway.enabled=false",

        "liquibase.url=jdbc:postgresql://localhost:5432/test",
        "liquibase.user=flyway",
        "liquibase.password=password",

        "spring.liquibase.url=jdbc:postgresql://localhost:5432/test",
        "spring.liquibase.user=flyway",
        "spring.liquibase.password=password",
})
@JdbcTest
public class SpringBootLiquibasePropertiesIntegrationTest {

    @ClassRule
    public static ConditionalTestRule conditionalTestRule = new ConditionalTestRule(TestAssumptions::assumeSpringBoot4SupportsJdbcTestAnnotation);

    @Configuration
    static class Config {}

    @Autowired
    private SpringLiquibase liquibase;

    @Autowired
    private DataSource dataSource;

    @Test
    public void test() {
        assertThat(liquibase.getDataSource()).isSameAs(dataSource);
    }
}
