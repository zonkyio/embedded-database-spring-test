package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.zonky.test.category.FlywayTestSuite;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.util.List;

import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;
import static io.zonky.test.db.util.ReflectionUtils.setField;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Category(FlywayTestSuite.class)
public class FlywayDescriptorTest {

    private static final FlywayVersion flywayVersion = FlywayClassUtils.getFlywayVersion();

    @Test
    public void testBasicFields() {
        FlywayWrapper wrapper1 = FlywayWrapper.newInstance();
        wrapper1.setLocations(ImmutableList.of("db/migration", "db/test/migration"));
        wrapper1.setSchemas(ImmutableList.of("schema1", "schema2", "schema3"));
        wrapper1.setTable("table1");
        wrapper1.setSqlMigrationPrefix("VM");
        wrapper1.setRepeatableSqlMigrationPrefix(flywayVersion.isLessThan("4") ? "R" : "RM");
        wrapper1.setSqlMigrationSeparator("---");
        wrapper1.setSqlMigrationSuffixes(ImmutableList.of(".xql"));
        wrapper1.setIgnoreMissingMigrations(flywayVersion.isGreaterThanOrEqualTo("4.1"));
        wrapper1.setIgnoreFutureMigrations(flywayVersion.isLessThan("4") || flywayVersion.isGreaterThanOrEqualTo("9"));
        wrapper1.setValidateOnMigrate(false);

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        wrapper2.setLocations(ImmutableList.of("db/migration", "db/test/migration"));
        wrapper2.setSchemas(ImmutableList.of("schema1", "schema2", "schema3"));
        wrapper2.setTable("table1");
        wrapper2.setSqlMigrationPrefix("VM");
        wrapper2.setRepeatableSqlMigrationPrefix(flywayVersion.isLessThan("4") ? "R" : "RM");
        wrapper2.setSqlMigrationSeparator("---");
        wrapper2.setSqlMigrationSuffixes(ImmutableList.of(".xql"));
        wrapper2.setIgnoreMissingMigrations(flywayVersion.isGreaterThanOrEqualTo("4.1"));
        wrapper2.setIgnoreFutureMigrations(flywayVersion.isLessThan("4") || flywayVersion.isGreaterThanOrEqualTo("9"));
        wrapper2.setValidateOnMigrate(false);

        FlywayDescriptor descriptor1 = FlywayDescriptor.from(wrapper1);
        FlywayDescriptor descriptor2 = FlywayDescriptor.from(wrapper2);

        assertThat(descriptor1).isEqualTo(descriptor2);

        FlywayWrapper wrapper3 = FlywayWrapper.newInstance();
        descriptor1.applyTo(wrapper3);
        FlywayDescriptor descriptor3 = FlywayDescriptor.from(wrapper3);

        assertThat(descriptor1).isEqualTo(descriptor3);
    }

    @Test
    public void testDynamicFields() throws ClassNotFoundException {
        Object mockResolvers = createMockResolvers();
        List<Object> mockCallbacks = createMockCallbacks();

        FlywayWrapper wrapper1 = FlywayWrapper.newInstance();
        Object config1 = wrapper1.getConfig();
        invokeMethod(config1, "setOutOfOrder", true);
        invokeMethod(config1, "setEncoding", flywayVersion.isLessThan("5.1") || flywayVersion.isGreaterThanOrEqualTo("9.9") ? "ISO-8859-1" : ISO_8859_1);
        invokeMethod(config1, "setPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"));
        if (flywayVersion.isLessThan("9.9")) {
            invokeMethod(config1, "setResolvers", mockResolvers);
            wrapper1.setCallbacks(mockCallbacks);
        } else {
            invokeMethod(config1, "setMigrationResolvers", ImmutableList.of("resolver1", "resolver2", "resolver3"));
            invokeMethod(config1, "setCallbacks", ImmutableList.of("db/migration/beforeMigrate.sql", "db/migration/afterMigrate.sql"));
        }

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        Object config2 = wrapper2.getConfig();
        invokeMethod(config2, "setOutOfOrder", true);
        invokeMethod(config2, "setEncoding", flywayVersion.isLessThan("5.1") || flywayVersion.isGreaterThanOrEqualTo("9.9") ? "ISO-8859-1" : ISO_8859_1);
        invokeMethod(config2, "setPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"));
        if (flywayVersion.isLessThan("9.9")) {
            invokeMethod(config2, "setResolvers", mockResolvers);
            wrapper2.setCallbacks(mockCallbacks);
        } else {
            invokeMethod(config2, "setMigrationResolvers", ImmutableList.of("resolver1", "resolver2", "resolver3"));
            invokeMethod(config2, "setCallbacks", ImmutableList.of("db/migration/beforeMigrate.sql", "db/migration/afterMigrate.sql"));
        }

        FlywayDescriptor descriptor1 = FlywayDescriptor.from(wrapper1);
        FlywayDescriptor descriptor2 = FlywayDescriptor.from(wrapper2);

        assertThat(descriptor1).isEqualTo(descriptor2);

        FlywayWrapper wrapper3 = FlywayWrapper.newInstance();
        descriptor1.applyTo(wrapper3);
        FlywayDescriptor descriptor3 = FlywayDescriptor.from(wrapper3);

        assertThat(descriptor1).isEqualTo(descriptor3);
    }

    @Test
    public void testSpecialFields() throws ClassNotFoundException {
        List<Object> mockMigrations1 = createMockMigrations();
        List<Object> mockCallbacks1 = createMockCallbacks();

        List<Object> mockMigrations2 = createMockMigrations();
        List<Object> mockCallbacks2 = createMockCallbacks();

        Object resourceProvider = null;
        Object classProvider = null;

        if (flywayVersion.isGreaterThanOrEqualTo("6.5")) {
            Class<?> resourceProviderType = ClassUtils.forName("org.flywaydb.core.api.ResourceProvider", null);
            Class<?> classProviderType = ClassUtils.forName("org.flywaydb.core.api.ClassProvider", null);
            resourceProvider = mock(resourceProviderType);
            classProvider = mock(classProviderType);
        }

        FlywayWrapper wrapper1 = FlywayWrapper.newInstance();
        wrapper1.setJavaMigration(mockMigrations1);
        wrapper1.setCallbacks(mockCallbacks1);
        wrapper1.setResourceProvider(resourceProvider);
        wrapper1.setJavaMigrationClassProvider(classProvider);

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        wrapper2.setJavaMigration(mockMigrations2);
        wrapper2.setCallbacks(mockCallbacks2);
        wrapper2.setResourceProvider(resourceProvider);
        wrapper2.setJavaMigrationClassProvider(classProvider);

        FlywayDescriptor descriptor1 = FlywayDescriptor.from(wrapper1);
        FlywayDescriptor descriptor2 = FlywayDescriptor.from(wrapper2);

        assertThat(descriptor1).isEqualTo(descriptor2);

        FlywayWrapper wrapper3 = FlywayWrapper.newInstance();
        descriptor1.applyTo(wrapper3);
        FlywayDescriptor descriptor3 = FlywayDescriptor.from(wrapper3);

        assertThat(descriptor1).isEqualTo(descriptor3);

        FlywayWrapper wrapper4 = FlywayWrapper.newInstance();
        FlywayDescriptor descriptor4 = FlywayDescriptor.from(wrapper4);

        assertThat(descriptor1).isNotEqualTo(descriptor4);
    }

    @Test
    public void testEnvsFields() {
        FlywayWrapper wrapper1 = FlywayWrapper.newInstance();
        Object envConfig1 = wrapper1.getEnvConfig();
        if (envConfig1 != null) {
            invokeMethod(envConfig1, "setUser", "user1");
            invokeMethod(envConfig1, "setPassword", "pass1");
            invokeMethod(envConfig1, "setSchemas", ImmutableList.of("schema1", "schema2", "schema3"));
            invokeMethod(envConfig1, "setJdbcProperties", ImmutableMap.of("key1", "value1", "key2", "value2"));
        }

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        Object envConfig2 = wrapper2.getEnvConfig();
        if (envConfig2 != null) {
            invokeMethod(envConfig2, "setUser", "user1");
            invokeMethod(envConfig2, "setPassword", "pass1");
            invokeMethod(envConfig2, "setSchemas", ImmutableList.of("schema1", "schema2", "schema3"));
            invokeMethod(envConfig2, "setJdbcProperties", ImmutableMap.of("key1", "value1", "key2", "value2"));
        }

        FlywayDescriptor descriptor1 = FlywayDescriptor.from(wrapper1);
        FlywayDescriptor descriptor2 = FlywayDescriptor.from(wrapper2);

        assertThat(descriptor1).isEqualTo(descriptor2);

        FlywayWrapper wrapper3 = FlywayWrapper.newInstance();
        descriptor1.applyTo(wrapper3);
        FlywayDescriptor descriptor3 = FlywayDescriptor.from(wrapper3);

        assertThat(descriptor1).isEqualTo(descriptor3);
    }

    @Test
    public void testPluginFields() {
        FlywayWrapper wrapper1 = FlywayWrapper.newInstance();
        wrapper1.getConfigurationExtensions().stream()
                .filter(o -> o.getClass().getSimpleName().equals("PostgreSQLConfigurationExtension"))
                .forEach(o -> invokeMethod(o, "setTransactionalLock", false));

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        wrapper2.getConfigurationExtensions().stream()
                .filter(o -> o.getClass().getSimpleName().equals("PostgreSQLConfigurationExtension"))
                .forEach(o -> invokeMethod(o, "setTransactionalLock", false));

        FlywayDescriptor descriptor1 = FlywayDescriptor.from(wrapper1);
        FlywayDescriptor descriptor2 = FlywayDescriptor.from(wrapper2);

        assertThat(descriptor1).isEqualTo(descriptor2);

        FlywayWrapper wrapper3 = FlywayWrapper.newInstance();
        descriptor1.applyTo(wrapper3);
        FlywayDescriptor descriptor3 = FlywayDescriptor.from(wrapper3);

        assertThat(descriptor1).isEqualTo(descriptor3);
    }

    @Test
    public void testExcludedFields() {
        FlywayWrapper wrapper1 = FlywayWrapper.newInstance();
        Object config1 = wrapper1.getConfig();
        if (flywayVersion.isLessThan("9.9")) {
            setField(config1, "classLoader", mock(ClassLoader.class));
            setField(config1, "dataSource", mock(DataSource.class));
        }
        if (flywayVersion.isLessThan("5.1")) {
            setField(config1, "dbConnectionInfoPrinted", true);
        }

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        Object config2 = wrapper2.getConfig();
        if (flywayVersion.isLessThan("9.9")) {
            setField(config2, "classLoader", mock(ClassLoader.class));
            setField(config2, "dataSource", mock(DataSource.class));
        }
        if (flywayVersion.isLessThan("5.1")) {
            setField(config2, "dbConnectionInfoPrinted", false);
        }

        FlywayDescriptor descriptor1 = FlywayDescriptor.from(wrapper1);
        FlywayDescriptor descriptor2 = FlywayDescriptor.from(wrapper2);

        assertThat(descriptor1).isEqualTo(descriptor2);

        FlywayWrapper wrapper3 = FlywayWrapper.newInstance();
        descriptor1.applyTo(wrapper3);
        FlywayDescriptor descriptor3 = FlywayDescriptor.from(wrapper3);

        assertThat(descriptor1).isEqualTo(descriptor3);
    }

    private static Object createMockResolvers() {
        MigrationResolver[] resolvers = new MigrationResolver[3];
        for (int i = 0; i < resolvers.length; i++) {
            resolvers[i] = mock(MigrationResolver.class);
        }
        return resolvers;
    }

    private static List<Object> callbacks;

    private static List<Object> createMockCallbacks() throws ClassNotFoundException {
        if (flywayVersion.isGreaterThanOrEqualTo("6")) { // Flyway 6 is required for BaseCallback
            return ImmutableList.of(
                    new TestCallback("1"),
                    new TestCallback("2"),
                    new TestCallback("3"));
        } else {
            if (callbacks == null) {
                Class<?> callbackType;
                if (flywayVersion.isGreaterThanOrEqualTo("5.1")) {
                    callbackType = ClassUtils.forName("org.flywaydb.core.api.callback.Callback", null);
                } else {
                    callbackType = ClassUtils.forName("org.flywaydb.core.api.callback.FlywayCallback", null);
                }
                callbacks = ImmutableList.of(mock(callbackType), mock(callbackType), mock(callbackType));
            }
            return callbacks;
        }
    }

    private static class TestCallback extends BaseCallback {

        private final String testParameter;

        private TestCallback(String testParameter) {
            this.testParameter = testParameter;
        }

        @Override
        public void handle(Event event, org.flywaydb.core.api.callback.Context context) {}
    }

    private static List<Object> createMockMigrations() {
        if (flywayVersion.isGreaterThanOrEqualTo("6")) {
            return ImmutableList.of(
                    new V1__TestJavaMigration("1"),
                    new V1__TestJavaMigration("2"),
                    new V1__TestJavaMigration("3"));
        } else {
            return ImmutableList.of();
        }
    }

    private static class V1__TestJavaMigration extends BaseJavaMigration {

        private final String testParameter;

        private V1__TestJavaMigration(String testParameter) {
            this.testParameter = testParameter;
        }

        @Override
        public void migrate(Context context) throws Exception {}
    }
}