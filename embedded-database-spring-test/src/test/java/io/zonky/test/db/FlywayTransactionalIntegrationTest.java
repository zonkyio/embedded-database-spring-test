package io.zonky.test.db;

import com.google.common.collect.ImmutableList;
import io.zonky.test.category.FlywayTests;
import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.flyway.FlywayWrapper;
import io.zonky.test.db.flyway.preparer.MigrateFlywayDatabasePreparer;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;

import javax.sql.DataSource;

import static io.zonky.test.db.context.DataSourceContext.State.DIRTY;
import static io.zonky.test.db.context.DataSourceContext.State.FRESH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

@RunWith(SpringRunner.class)
@Category(FlywayTests.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
@DataJpaTest
public class FlywayTransactionalIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeTransaction
    public void beforeTransaction() {
        assertThat(dataSourceContext.getState()).isEqualTo(FRESH);
    }

    @Before
    public void beforeTestExecution() {
        assertThat(dataSourceContext.getState()).isEqualTo(DIRTY);
    }

    @AfterTransaction
    public void afterTransaction() throws Exception {
        assertThat(dataSourceContext.getState()).isEqualTo(DIRTY);

        InOrder inOrder = inOrder(dataSourceContext);

        inOrder.verify(dataSourceContext).apply(any(MigrateFlywayDatabasePreparer.class));
        inOrder.verify(dataSourceContext).reset();
        inOrder.verify(dataSourceContext, times(3)).getState();
        inOrder.verify(dataSourceContext, times(2)).getTarget();
        inOrder.verify(dataSourceContext, times(2)).getState();
    }

    @Test
    @FlywayTest
    public void test() {
        assertThat(dataSourceContext).isNotNull();
        assertThat(jdbcTemplate.queryForList("select * from test.person")).hasSize(1);
    }
}
