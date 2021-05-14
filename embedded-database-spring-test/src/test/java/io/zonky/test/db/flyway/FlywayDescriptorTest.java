package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.zonky.test.category.FlywayTestSuite;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.lang.reflect.Array;

import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;
import static io.zonky.test.db.util.ReflectionUtils.setField;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Category(FlywayTestSuite.class)
public class FlywayDescriptorTest {

    private static final int flywayVersion = FlywayClassUtils.getFlywayVersion();

    @Test
    public void testBasicFields() {
        FlywayWrapper wrapper1 = FlywayWrapper.newInstance();
        wrapper1.setLocations(ImmutableList.of("db/migration", "db/test/migration"));
        wrapper1.setSchemas(ImmutableList.of("schema1", "schema2", "schema3"));
        wrapper1.setTable("table1");
        wrapper1.setSqlMigrationPrefix("VM");
        wrapper1.setRepeatableSqlMigrationPrefix(flywayVersion < 40 ? "R" : "RM");
        wrapper1.setSqlMigrationSeparator("---");
        wrapper1.setSqlMigrationSuffixes(ImmutableList.of(".xql"));
        wrapper1.setIgnoreMissingMigrations(flywayVersion >= 41);
        wrapper1.setIgnoreFutureMigrations(flywayVersion < 40);
        wrapper1.setValidateOnMigrate(false);

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        wrapper2.setLocations(ImmutableList.of("db/migration", "db/test/migration"));
        wrapper2.setSchemas(ImmutableList.of("schema1", "schema2", "schema3"));
        wrapper2.setTable("table1");
        wrapper2.setSqlMigrationPrefix("VM");
        wrapper2.setRepeatableSqlMigrationPrefix(flywayVersion < 40 ? "R" : "RM");
        wrapper2.setSqlMigrationSeparator("---");
        wrapper2.setSqlMigrationSuffixes(ImmutableList.of(".xql"));
        wrapper2.setIgnoreMissingMigrations(flywayVersion >= 41);
        wrapper2.setIgnoreFutureMigrations(flywayVersion < 40);
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
        Object mockCallbacks = createMockCallbacks();

        FlywayWrapper wrapper1 = FlywayWrapper.newInstance();
        Object config1 = wrapper1.getConfig();
        invokeMethod(config1, "setOutOfOrder", true);
        invokeMethod(config1, "setEncoding", flywayVersion < 51 ? "ISO-8859-1" : ISO_8859_1);
        invokeMethod(config1, "setPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"));
        invokeMethod(config1, "setResolvers", mockResolvers);
        invokeMethod(config1, "setCallbacks", mockCallbacks);

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        Object config2 = wrapper2.getConfig();
        invokeMethod(config2, "setOutOfOrder", true);
        invokeMethod(config2, "setEncoding", flywayVersion < 51 ? "ISO-8859-1" : ISO_8859_1);
        invokeMethod(config2, "setPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"));
        invokeMethod(config2, "setResolvers", mockResolvers);
        invokeMethod(config2, "setCallbacks", mockCallbacks);

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
        setField(config1, "classLoader",  mock(ClassLoader.class));
        setField(config1, "dataSource", mock(DataSource.class));
        if (flywayVersion < 51) {
            setField(config1, "dbConnectionInfoPrinted", true);
        }

        FlywayWrapper wrapper2 = FlywayWrapper.newInstance();
        Object config2 = wrapper2.getConfig();
        setField(config2, "classLoader",  mock(ClassLoader.class));
        setField(config2, "dataSource", mock(DataSource.class));
        if (flywayVersion < 51) {
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

    private static Object createMockCallbacks() throws ClassNotFoundException {
        final Class<?> callbackType;
        if (flywayVersion >= 51) {
            callbackType = ClassUtils.forName("org.flywaydb.core.api.callback.Callback", null);
        } else {
            callbackType = ClassUtils.forName("org.flywaydb.core.api.callback.FlywayCallback", null);
        }

        Object[] callbacks = (Object[]) Array.newInstance(callbackType, 3);
        for (int i = 0; i < callbacks.length; i++) {
            callbacks[i] = mock(callbackType);
        }
        return callbacks;
    }
}