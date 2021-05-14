package io.zonky.test.db.context;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import io.zonky.test.category.SpringTestSuite;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import io.zonky.test.db.support.DatabaseDefinition;
import io.zonky.test.db.support.DefaultProviderResolver;
import io.zonky.test.db.support.ProviderDescriptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mariadb.jdbc.MariaDbDataSource;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.Environment;

import java.util.HashSet;
import java.util.Set;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
@Category(SpringTestSuite.class)
public class DefaultProviderResolverTest {

    @Mock
    private Environment environment;

    private TestClassLoader classLoader;

    private DefaultProviderResolver resolver;

    @Before
    public void setUp() throws Exception {
        classLoader = new TestClassLoader();
        resolver = new DefaultProviderResolver(environment, classLoader);
    }

    @Test
    public void explicitParameters() {
        ProviderDescriptor descriptor = resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.MSSQL, DatabaseProvider.DOCKER));

        assertThat(descriptor.getDatabaseName()).isEqualTo("mssql");
        assertThat(descriptor.getProviderName()).isEqualTo("docker");

        verifyNoInteractions(environment);
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
    public void configurationPropertiesContainingDefaultValues() {
        when(environment.getProperty("zonky.test.database.type")).thenReturn("auto");
        when(environment.getProperty("zonky.test.database.provider", "docker")).thenReturn("default");

        classLoader.addClass(SQLServerDataSource.class);

        ProviderDescriptor descriptor = resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT));

        assertThat(descriptor.getDatabaseName()).isEqualTo("mssql");
        assertThat(descriptor.getProviderName()).isEqualTo("docker");
    }

    @Test
    public void autoDetectionMode() {
        classLoader.addClass(MysqlDataSource.class);

        ProviderDescriptor descriptor = resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT));

        assertThat(descriptor.getDatabaseName()).isEqualTo("mysql");
        assertThat(descriptor.getProviderName()).isEqualTo("docker");
    }

    @Test
    public void noDatabaseDetected() {
        assertThatCode(() -> resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no database driver detected");
    }

    @Test
    public void multipleDatabasesDetected() {
        classLoader.addClass(PGSimpleDataSource.class);
        classLoader.addClass(MariaDbDataSource.class);

        assertThatCode(() -> resolver.getDescriptor(new DatabaseDefinition("", DatabaseType.AUTO, DatabaseProvider.DEFAULT)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple database drivers detected");
    }

    private static class TestClassLoader extends ClassLoader {

        private final Set<Class<?>> availableClasses = new HashSet<>();

        public void addClass(Class<?> clazz) {
            availableClasses.add(clazz);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return availableClasses.stream()
                    .filter(cls -> cls.getName().equals(name))
                    .findAny().orElseThrow(ClassNotFoundException::new);
        }
    }
}