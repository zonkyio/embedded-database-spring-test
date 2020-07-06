package io.zonky.test.db;

import com.google.common.collect.ImmutableList;
import io.zonky.test.category.FlywayTests;
import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.flyway.FlywayWrapper;
import io.zonky.test.db.provider.DatabaseProvider;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode.BEFORE_EACH_TEST_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

@RunWith(SpringRunner.class)
@Category(FlywayTests.class)
@TestExecutionListeners(
        mergeMode = MERGE_WITH_DEFAULTS,
        listeners = FlywayMethodRefreshIntegrationTest.class
)
@FlywayTest(locationsForMigrate = "db/test_migration/appendable")
@AutoConfigureEmbeddedDatabase(type = POSTGRES, refreshMode = BEFORE_EACH_TEST_METHOD) // TODO
@ContextConfiguration
public class FlywayMethodRefreshIntegrationTest extends AbstractTestExecutionListener {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";
    private static final String SQL_INSERT_PERSON = "insert into test.person (id, first_name, last_name) values (?, ?, ?);";

    @Configuration
    static class Config {

        @Bean(initMethod = "migrate")
        public Flyway flyway(DataSource dataSource) {
            FlywayWrapper wrapper = FlywayWrapper.newInstance();
            wrapper.setDataSource(dataSource);
            wrapper.setSchemas(ImmutableList.of("test"));
            return wrapper.getFlyway();
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @SpyBean(reset = MockReset.NONE)
    private DataSourceContext dataSourceContext;

    @SpyBean(reset = MockReset.NONE, name = "dockerPostgresDatabaseProvider")
    private DatabaseProvider databaseProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void afterTestClass(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        DataSourceContext dataSourceContext = applicationContext.getBean(DataSourceContext.class);
        DatabaseProvider databaseProvider = applicationContext.getBean("dockerPostgresDatabaseProvider", DatabaseProvider.class);

//        verify(dataSourceContext, times(4)).reset();
//        verify(dataSourceContext, times(1)).apply(any());
//        verify(databaseProvider, times(3)).createDatabase(any());

        Mockito.reset(dataSourceContext, databaseProvider);
    }

    @Test
    public void isolatedTest1() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(2);

        assertThat(persons).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"));

        jdbcTemplate.update(SQL_INSERT_PERSON, 3, "Will", "Smith");
    }

    @Test
    public void isolatedTest2() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(2);

        assertThat(persons).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"));

        jdbcTemplate.update(SQL_INSERT_PERSON, 3, "Will", "Smith");
    }

    @Test
    public void isolatedTest3() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(2);

        assertThat(persons).extracting("id", "first_name", "last_name").containsExactlyInAnyOrder(
                tuple(1L, "Dave", "Syer"),
                tuple(2L, "Tom", "Hanks"));

        jdbcTemplate.update(SQL_INSERT_PERSON, 3, "Will", "Smith");
    }
}
