package io.zonky.test.db;

import io.zonky.test.category.SpringTestSuite;
import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.support.SpyPostProcessor;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

@RunWith(SpringRunner.class)
@Category(SpringTestSuite.class)
@TestExecutionListeners(
        mergeMode = MERGE_WITH_DEFAULTS,
        listeners = SpringSqlAnnotationRefreshIntegrationTest.class
)
@Sql(statements = "create schema if not exists test")
@Sql(scripts = {
        "/db/migration/V0001_1__create_person_table.sql",
        "/db/migration/V0002_1__rename_surname_column.sql"
})
@AutoConfigureEmbeddedDatabase(type = POSTGRES, refresh = AFTER_EACH_TEST_METHOD)
@ContextConfiguration
public class SpringSqlAnnotationRefreshIntegrationTest extends AbstractTestExecutionListener {

    private static final String SQL_SELECT_PERSONS = "select * from test.person";
    private static final String SQL_INSERT_PERSON = "insert into test.person (id, first_name, last_name) values (?, ?, ?);";

    @Configuration
    static class Config {

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public BeanPostProcessor spyPostProcessor() {
            return new SpyPostProcessor((bean, beanName) ->
                    bean instanceof DatabaseContext || beanName.equals("dockerPostgresDatabaseProvider"));
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        DatabaseContext databaseContext = applicationContext.getBean(DatabaseContext.class);
        DatabaseProvider databaseProvider = applicationContext.getBean("dockerPostgresDatabaseProvider", DatabaseProvider.class);

        verify(databaseContext, times(4)).reset();
        verify(databaseContext, never()).apply(any());
        verify(databaseProvider, times(3)).createDatabase(any());

        Mockito.reset(databaseContext, databaseProvider);
    }

    @Test
    public void isolatedTest1() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        jdbcTemplate.update(SQL_INSERT_PERSON, 2, "Tom", "Hanks");
    }

    @Test
    public void isolatedTest2() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        jdbcTemplate.update(SQL_INSERT_PERSON, 2, "Will", "Smith");
    }

    @Test
    public void isolatedTest3() {
        List<Map<String, Object>> persons = jdbcTemplate.queryForList(SQL_SELECT_PERSONS);
        assertThat(persons).isNotNull().hasSize(1);

        jdbcTemplate.update(SQL_INSERT_PERSON, 2, "Eddie", "Murphy");
    }
}
