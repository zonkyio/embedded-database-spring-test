package io.zonky.test.db.provider;

import io.zonky.test.category.MSSQLTestSuite;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.provider.mssql.MsSQLEmbeddedDatabase;
import io.zonky.test.support.ConditionalTestRule;
import io.zonky.test.support.TestAssumptions;
import org.junit.ClassRule;
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

@RunWith(SpringRunner.class)
@Category(MSSQLTestSuite.class)
@AutoConfigureEmbeddedDatabase(type = MSSQL)
@ContextConfiguration
public class DockerMSSQLProviderIntegrationTest {

    @ClassRule
    public static ConditionalTestRule conditionalTestRule = new ConditionalTestRule(TestAssumptions::assumeLicenceAcceptance);

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDataSource() throws SQLException {
        assertThat(dataSource.unwrap(MsSQLEmbeddedDatabase.class).getJdbcUrl()).contains("password=A_Str0ng_Required_Password");
    }
}
