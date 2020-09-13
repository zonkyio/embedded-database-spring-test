package io.zonky.test.db;

import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.provider.DatabaseProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

@RunWith(SpringRunner.class)
@TestExecutionListeners(
        mergeMode = MERGE_WITH_DEFAULTS,
        listeners = DatabaseRefreshIntegrationTest.class
)
@AutoConfigureEmbeddedDatabase(type = POSTGRES, refreshMode = AFTER_EACH_TEST_METHOD)
@ContextConfiguration
public class DatabaseRefreshIntegrationTest extends AbstractTestExecutionListener {

    private static final String SQL_SELECT_ADDRESS = "select * from test.address";
    private static final String SQL_INSERT_ADDRESS = "insert into test.address (id, street) values (?, ?);";

    @Configuration
    static class Config {

        @Bean
        public TestDatabaseInitializer testDatabaseInitializer(DataSource dataSource, ResourceLoader resourceLoader) {
            return new TestDatabaseInitializer(dataSource, resourceLoader);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    public static class TestDatabaseInitializer {

        private final DataSource dataSource;
        private final ResourceLoader resourceLoader;

        public TestDatabaseInitializer(DataSource dataSource, ResourceLoader resourceLoader) {
            this.dataSource = dataSource;
            this.resourceLoader = resourceLoader;
        }

        @PostConstruct
        public void init() {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(this.resourceLoader.getResource("db/create_address_table.sql"));
            DatabasePopulatorUtils.execute(populator, this.dataSource);
        }
    }

    @SpyBean(reset = MockReset.NONE)
    private DataSourceContext dataSourceContext;

    @SpyBean(reset = MockReset.NONE, name = "dockerPostgresDatabaseProvider")
    private DatabaseProvider databaseProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        DataSourceContext dataSourceContext = applicationContext.getBean(DataSourceContext.class);
        DatabaseProvider databaseProvider = applicationContext.getBean("dockerPostgresDatabaseProvider", DatabaseProvider.class);

        verify(dataSourceContext, times(4)).reset();
        verify(dataSourceContext, never()).apply(any());
        verify(databaseProvider, times(3)).createDatabase(any());

        Mockito.reset(dataSourceContext, databaseProvider);
    }

    @Test
    public void isolatedTest1() {
        List<Map<String, Object>> addresses = jdbcTemplate.queryForList(SQL_SELECT_ADDRESS);
        assertThat(addresses).isNotNull().hasSize(0);

        jdbcTemplate.update(SQL_INSERT_ADDRESS, 1, "address");
    }

    @Test
    public void isolatedTest2() {
        List<Map<String, Object>> addresses = jdbcTemplate.queryForList(SQL_SELECT_ADDRESS);
        assertThat(addresses).isNotNull().hasSize(0);

        jdbcTemplate.update(SQL_INSERT_ADDRESS, 1, "address");
    }

    @Test
    public void isolatedTest3() {
        List<Map<String, Object>> addresses = jdbcTemplate.queryForList(SQL_SELECT_ADDRESS);
        assertThat(addresses).isNotNull().hasSize(0);

        jdbcTemplate.update(SQL_INSERT_ADDRESS, 1, "address");
    }
}
