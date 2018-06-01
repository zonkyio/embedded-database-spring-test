package io.zonky.test.db;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(beanName = "dataSource")
@ContextConfiguration
public class DatabaseCustomizerIntegrationTest {

    @Configuration
    static class Config {

        @Bean(initMethod = "migrate")
        public Flyway flyway(DataSource dataSource) {
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setSchemas("test", "unique");
            return flyway;
        }

        @Bean
        public Consumer<EmbeddedPostgres.Builder> embeddedPostgresCustomizer() {
            return builder -> builder.setPort(33333);
        }
    }

    @Autowired
    private DataSource dataSource;

    @Test
    public void primaryDataSourceShouldBeReplaced() throws Exception {
        assertThat(dataSource.unwrap(PGSimpleDataSource.class).getPortNumber()).isEqualTo(33333);
    }
}
