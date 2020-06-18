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

import io.zonky.test.category.FlywayTests;
import io.zonky.test.db.flyway.FlywayWrapper;
import org.flywaydb.core.Flyway;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(FlywayTests.class)
@AutoConfigureEmbeddedDatabase
@TestPropertySource(properties = {
        "liquibase.enabled=false",
        "spring.liquibase.enabled=false",

        "flyway.url=jdbc:postgresql://localhost:5432/test",
        "flyway.user=flyway",
        "flyway.password=password",
        "flyway.schemas=test",

        "spring.flyway.url=jdbc:postgresql://localhost:5432/test",
        "spring.flyway.user=flyway",
        "spring.flyway.password=password",
        "spring.flyway.schemas=test"
})
@DataJpaTest
public class SpringBootFlywayPropertiesIntegrationTest {

    @Configuration
    static class Config {}

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    public void test() {
        FlywayWrapper wrapper = FlywayWrapper.of(flyway);
        assertThat(wrapper.getDataSource()).isSameAs(dataSource);
    }
}
