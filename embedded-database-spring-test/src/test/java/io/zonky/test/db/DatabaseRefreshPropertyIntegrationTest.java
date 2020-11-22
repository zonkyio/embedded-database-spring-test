package io.zonky.test.db;

import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.support.SpyPostProcessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

@RunWith(SpringRunner.class)
@TestExecutionListeners(
        mergeMode = MERGE_WITH_DEFAULTS,
        listeners = DatabaseRefreshPropertyIntegrationTest.class
)
@TestPropertySource(properties = "zonky.test.database.refresh=after-each-test-method")
@AutoConfigureEmbeddedDatabase(type = POSTGRES)
@ContextConfiguration
public class DatabaseRefreshPropertyIntegrationTest extends AbstractTestExecutionListener {

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

        @Bean
        public BeanPostProcessor spyPostProcessor() {
            return new SpyPostProcessor((bean, beanName) ->
                    bean instanceof DatabaseContext || beanName.equals("dockerPostgresDatabaseProvider"));
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
