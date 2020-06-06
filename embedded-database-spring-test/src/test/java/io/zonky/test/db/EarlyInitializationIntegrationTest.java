package io.zonky.test.db;

import com.google.common.collect.ImmutableList;
import io.zonky.test.category.FlywayTests;
import io.zonky.test.db.flyway.FlywayWrapper;
import org.flywaydb.core.Flyway;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Category(FlywayTests.class)
@AutoConfigureEmbeddedDatabase
@ContextConfiguration
public class EarlyInitializationIntegrationTest {

    @Configuration
    static class Config {

        @Bean
        public BeanFactoryPostProcessor beanFactoryPostProcessor() {
            return new TestBeanFactoryPostProcessor();
        }

        @Bean(initMethod = "migrate")
        public Flyway flyway(DataSource dataSource) {
            FlywayWrapper wrapper = FlywayWrapper.newInstance();
            wrapper.setDataSource(dataSource);
            wrapper.setSchemas(ImmutableList.of("test"));
            return wrapper.getFlyway();
        }
    }

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDataSource() {
        assertThat(dataSource).isNotNull();
    }

    private static class TestBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            beanFactory.getBeanNamesForType(DataSource.class, true, true);
        }
    }
}
