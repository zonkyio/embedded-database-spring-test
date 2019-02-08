/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Stopwatch;
import io.zonky.test.category.FlywayIntegrationTests;
import io.zonky.test.db.flyway.DefaultFlywayDataSourceContext;
import io.zonky.test.db.flyway.FlywayDataSourceContext;
import org.flywaydb.core.Flyway;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(FlywayIntegrationTests.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
@ContextConfiguration
public class AsyncFlywayInitializationIntegrationTest {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";

    @Configuration
    static class Config {

        @Bean
        public Flyway flyway(DataSource dataSource) {
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setSchemas("test");
            flyway.setLocations("db/migration", "db/test_migration/slow");
            return flyway;
        }

        @Bean
        public FlywayDataSourceContext flywayDataSourceContext(TaskExecutor bootstrapExecutor) {
            DefaultFlywayDataSourceContext dataSourceContext = new DefaultFlywayDataSourceContext();
            dataSourceContext.setBootstrapExecutor(bootstrapExecutor);
            return dataSourceContext;
        }

        @Bean
        public LongTimeInitializingBean longTimeInitializingBean(DataSource dataSource) {
            return new LongTimeInitializingBean(dataSource);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public TaskExecutor bootstrapExecutor() {
            return new SimpleAsyncTaskExecutor("bootstrapExecutor-");
        }
    }

    @Autowired
    private LongTimeInitializingBean longTimeInitializingBean;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test(timeout = 10000)
    public void loadDefaultMigrations() {
        Duration duration = longTimeInitializingBean.getInitializationDuration();
        assertThat(duration).isGreaterThan(Duration.ofSeconds(1));

        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);
    }

    private static class LongTimeInitializingBean implements InitializingBean {

        private final DataSource dataSource;

        private Duration initializationDuration;

        private LongTimeInitializingBean(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public Duration getInitializationDuration() {
            return initializationDuration;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            Stopwatch stopwatch = Stopwatch.createStarted();
            dataSource.getConnection().close();
            initializationDuration = stopwatch.elapsed();
        }
    }
}
