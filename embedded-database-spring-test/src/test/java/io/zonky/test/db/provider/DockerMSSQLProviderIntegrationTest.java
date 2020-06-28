package io.zonky.test.db.provider;

import io.zonky.test.category.StaticTests;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.provider.mssql.MsSQLEmbeddedDatabase;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.SQLException;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.MSSQL;
import static org.assertj.core.api.Assertions.assertThat;

@Category(StaticTests.class)
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase(type = MSSQL)
@ContextConfiguration
public class DockerMSSQLProviderIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDataSource() throws SQLException {
        assertThat(dataSource.unwrap(MsSQLEmbeddedDatabase.class).getUrl()).contains("password=A_Str0ng_Required_Password");
    }
}
