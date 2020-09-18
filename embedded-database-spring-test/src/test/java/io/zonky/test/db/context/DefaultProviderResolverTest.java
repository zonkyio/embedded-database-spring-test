package io.zonky.test.db.context;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import io.zonky.test.db.support.DatabaseDefinition;
import io.zonky.test.db.support.DefaultProviderResolver;
import io.zonky.test.db.support.ProviderDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mariadb.jdbc.MariaDbDataSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.Environment;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultProviderResolverTest {

    @Mock
    private Environment environment;
    @Mock
    private ClassLoader classLoader;

    @InjectMocks
    private DefaultProviderResolver resolver;

    @Test
    public void explicitParameters() {
        ProviderDescriptor descriptor = resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.MSSQL, DatabaseProvider.DOCKER));

        assertThat(descriptor.getDatabaseName()).isEqualTo("mssql");
        assertThat(descriptor.getProviderName()).isEqualTo("docker");

        verifyZeroInteractions(environment, classLoader);
    }

    @Test
    public void configurationProperties() {
        when(environment.getProperty("zonky.test.database.type")).thenReturn("postgres");
        when(environment.getProperty("zonky.test.database.provider")).thenReturn("opentable");

        ProviderDescriptor descriptor = resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT));

        assertThat(descriptor.getDatabaseName()).isEqualTo("postgres");
        assertThat(descriptor.getProviderName()).isEqualTo("opentable");
    }

    @Test
    public void configurationPropertiesContainingDefaultValues() throws ClassNotFoundException {
        when(environment.getProperty("zonky.test.database.type")).thenReturn("auto");
        when(environment.getProperty("zonky.test.database.provider", "docker")).thenReturn("default");

        doThrow(ClassNotFoundException.class).when(classLoader).loadClass(any());
        doReturn(SQLServerDataSource.class).when(classLoader).loadClass("com.microsoft.sqlserver.jdbc.SQLServerDataSource");

        ProviderDescriptor descriptor = resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT));

        assertThat(descriptor.getDatabaseName()).isEqualTo("mssql");
        assertThat(descriptor.getProviderName()).isEqualTo("docker");
    }

    @Test
    public void autoDetectionMode() throws ClassNotFoundException {
        doThrow(ClassNotFoundException.class).when(classLoader).loadClass(any());
        doReturn(MysqlDataSource.class).when(classLoader).loadClass("com.mysql.cj.jdbc.MysqlDataSource");

        ProviderDescriptor descriptor = resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT));

        assertThat(descriptor.getDatabaseName()).isEqualTo("mysql");
        assertThat(descriptor.getProviderName()).isEqualTo("docker");
    }

    @Test
    public void noDatabaseDetected() throws ClassNotFoundException {
        doThrow(ClassNotFoundException.class).when(classLoader).loadClass(any());

        assertThatCode(() -> resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no database detected");
    }

    @Test
    public void multipleDatabasesDetected() throws ClassNotFoundException {
        doThrow(ClassNotFoundException.class).when(classLoader).loadClass(any());
        doReturn(PGSimpleDataSource.class).when(classLoader).loadClass("org.postgresql.ds.PGSimpleDataSource");
        doReturn(MariaDbDataSource.class).when(classLoader).loadClass("org.mariadb.jdbc.MariaDbDataSource");

        assertThatCode(() -> resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple databases detected");
    }
}