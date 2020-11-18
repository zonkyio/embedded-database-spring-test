package io.zonky.test.db;

import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.provider.DatabaseProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = "zonky.test.database.replace=none")
@AutoConfigureEmbeddedDatabase(type = POSTGRES, refresh = AFTER_EACH_TEST_METHOD)
@ContextConfiguration
public class DatabaseReplacePropertyIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private DatabaseProvider dockerPostgresDatabaseProvider;

    @Test
    public void noDatabaseContextShouldExist() {
        assertThat(dockerPostgresDatabaseProvider).isNotNull();
        assertThat(applicationContext.getBeanNamesForType(DatabaseContext.class)).isEmpty();
    }
}
