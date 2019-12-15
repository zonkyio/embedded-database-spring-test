package io.zonky.test.db.flyway;

import io.zonky.test.db.flyway.preparer.BaselineFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.CleanFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.MigrateFlywayDatabasePreparer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.aop.framework.Advised;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.zonky.test.db.flyway.FlywayContextExtension.FlywayOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(MockitoJUnitRunner.class)
public class FlywayContextExtensionTest {

    @Mock
    private DataSourceContext dataSourceContext;

    private FlywayWrapper flywayWrapper;

    private FlywayContextExtension flywayExtension = new FlywayContextExtension();

    @Before
    public void setUp() {
        Advised dataSource = mock(Advised.class, withSettings().extraInterfaces(DataSource.class));
        when(dataSource.getTargetSource()).thenReturn(dataSourceContext);

        Flyway flyway = new Flyway();
        flyway.setLocations("db/migration");
        flyway.setDataSource((DataSource) dataSource);

        flywayWrapper = new FlywayWrapper(((Flyway) flywayExtension.postProcessBeforeInitialization(flyway, "flyway")));
    }

    @Test
    public void cleanFromExecutionListenerShouldBeDeferred() {
        Flyway flyway = flywayWrapper.getFlyway();
        FlywayTestExecutionListener.dbResetWithAnnotation(flyway::clean);

        assertThat(flywayExtension.pendingOperations).hasSize(1);
        verifyZeroInteractions(dataSourceContext);
    }

    @Test
    public void baselineFromExecutionListenerShouldBeDeferred() {
        Flyway flyway = flywayWrapper.getFlyway();
        FlywayTestExecutionListener.dbResetWithAnnotation(flyway::baseline);
        assertThat(flywayExtension.pendingOperations).hasSize(1);
    }

    @Test
    public void migrateFromExecutionListenerShouldBeDeferred() {
        Flyway flyway = flywayWrapper.getFlyway();
        FlywayTestExecutionListener.dbResetWithAnnotation(flyway::migrate);
        assertThat(flywayExtension.pendingOperations).hasSize(1);
    }

    @Test
    public void cleanOutsideExecutionListenerShouldBeProcessedImmediately() {
        Flyway flyway = flywayWrapper.getFlyway();
        flyway.clean();

        assertThat(flywayExtension.pendingOperations).isEmpty();
        verify(dataSourceContext).apply(cleanPreparer(flywayWrapper));
    }

    @Test
    public void baselineOutsideExecutionListenerShouldBeProcessedImmediately() {
        Flyway flyway = flywayWrapper.getFlyway();
        flyway.baseline();

        assertThat(flywayExtension.pendingOperations).isEmpty();
        verify(dataSourceContext).apply(baselinePreparer(flywayWrapper));
    }

    @Test
    public void migrateOutsideExecutionListenerShouldBeProcessedImmediately() {
        Flyway flyway = flywayWrapper.getFlyway();
        flyway.migrate();

        assertThat(flywayExtension.pendingOperations).isEmpty();
        verify(dataSourceContext).apply(migratePreparer(flywayWrapper));
    }

    @Test
    public void testResetWithDefaultLocations() {
        flywayExtension.pendingOperations.add(migrateOperation(flywayWrapper));
        flywayExtension.pendingOperations.add(cleanOperation(flywayWrapper));
        flywayExtension.pendingOperations.add(migrateOperation(flywayWrapper));

        flywayExtension.processPendingOperations();

        verify(dataSourceContext).reset();
        verifyNoMoreInteractions(dataSourceContext);
    }

    @Test
    public void testResetWithAppendableLocation() {
        flywayExtension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration")));
        flywayExtension.pendingOperations.add(cleanOperation(flywayWrapper));
        flywayExtension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration", "db/test_migration/appendable")));

        flywayExtension.processPendingOperations();

        InOrder inOrder = inOrder(dataSourceContext);
        inOrder.verify(dataSourceContext).reset();
        inOrder.verify(dataSourceContext).apply(migratePreparer(flywayWrapper, withLocations("db/test_migration/appendable"), ignoreMissingMigrations()));

        verifyNoMoreInteractions(dataSourceContext);
    }

    @Test
    public void testResetWithDependentLocations() {
        flywayExtension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration")));
        flywayExtension.pendingOperations.add(cleanOperation(flywayWrapper));
        flywayExtension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration", "db/test_migration/dependent")));

        flywayExtension.processPendingOperations();

        InOrder inOrder = inOrder(dataSourceContext);
        inOrder.verify(dataSourceContext).reset();
        inOrder.verify(dataSourceContext).apply(cleanPreparer(flywayWrapper));
        inOrder.verify(dataSourceContext).apply(migratePreparer(flywayWrapper, withLocations("db/migration", "db/test_migration/dependent")));

        verifyNoMoreInteractions(dataSourceContext);
    }

    @Test
    public void testResetWithSeparatedLocations() {
        flywayExtension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration")));
        flywayExtension.pendingOperations.add(cleanOperation(flywayWrapper));
        flywayExtension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/test_migration/separated")));

        flywayExtension.processPendingOperations();

        InOrder inOrder = inOrder(dataSourceContext);
        inOrder.verify(dataSourceContext).reset();
        inOrder.verify(dataSourceContext).apply(cleanPreparer(flywayWrapper));
        inOrder.verify(dataSourceContext).apply(migratePreparer(flywayWrapper, withLocations("db/test_migration/separated")));

        verifyNoMoreInteractions(dataSourceContext);
    }

    @Test
    public void squashOperations() {
        FlywayOperation cleanOperation = cleanOperation(flywayWrapper);
        FlywayOperation migrateOperation = migrateOperation(flywayWrapper);

        List<FlywayOperation> operations = ImmutableList.of(
                cleanOperation, migrateOperation, cleanOperation, cleanOperation, migrateOperation);

        assertThat(flywayExtension.squashOperations(operations))
                .containsExactly(cleanOperation, migrateOperation);
    }

    @Test
    public void defaultLocations() {
        boolean result = flywayExtension.isAppendable(flywayWrapper, new String[]{"classpath:db/migration"});
        assertThat(result).isTrue();
    }

    @Test
    public void appendableLocation() {
        boolean result = flywayExtension.isAppendable(flywayWrapper, new String[]{"classpath:db/migration", "classpath:db/test_migration/appendable"});
        assertThat(result).isTrue();
    }

    @Test
    public void dependentLocations() {
        boolean result = flywayExtension.isAppendable(flywayWrapper, new String[]{"classpath:db/migration", "classpath:db/test_migration/dependent"});
        assertThat(result).isFalse();
    }

    @Test
    public void separatedLocations() {
        boolean result = flywayExtension.isAppendable(flywayWrapper, new String[]{"classpath:db/test_migration/separated"});
        assertThat(result).isFalse();
    }

    @Test
    public void resolveTestLocations() {
        String[] testLocations = flywayExtension.resolveTestLocations(
                flywayWrapper, new String[]{"classpath:db/migration", "classpath:db/test_migration/dependent"});

        assertThat(testLocations).containsExactly("classpath:db/test_migration/dependent");
    }

    @Test
    public void findFirstVersion() {
        MigrationVersion firstVersion = flywayExtension.findFirstVersion(
                flywayWrapper, "db/test_migration/dependent");

        assertThat(firstVersion).isNotNull();
        assertThat(firstVersion.getVersion()).isEqualTo("0001.2");
    }

    @Test
    public void findLastVersion() {
        MigrationVersion firstVersion = flywayExtension.findLastVersion(
                flywayWrapper, "db/test_migration/dependent");

        assertThat(firstVersion).isNotNull();
        assertThat(firstVersion.getVersion()).isEqualTo("0999.1");
    }

    @Test
    public void resolveMigrations() {
        Collection<ResolvedMigration> resolvedMigrations = flywayExtension.resolveMigrations(
                flywayWrapper, new String[]{"db/migration", "db/test_migration/dependent"});

        assertThat(resolvedMigrations).extracting("version.version")
                .containsExactly("0001.1", "0001.2", "0002.1", "0999.1", null);
    }

    private static FlywayOperation cleanOperation(FlywayWrapper wrapper) {
        return new FlywayOperation(wrapper, cleanPreparer(wrapper));
    }

    private static FlywayOperation migrateOperation(FlywayWrapper wrapper, FlywayPreparerCustomizer... customizers) {
        return new FlywayOperation(wrapper, migratePreparer(wrapper, customizers));
    }

    private static CleanFlywayDatabasePreparer cleanPreparer(FlywayWrapper wrapper) {
        return new CleanFlywayDatabasePreparer(wrapper.getFlyway());
    }

    private static BaselineFlywayDatabasePreparer baselinePreparer(FlywayWrapper wrapper) {
        return new BaselineFlywayDatabasePreparer(wrapper.getFlyway());
    }

    private static MigrateFlywayDatabasePreparer migratePreparer(FlywayWrapper wrapper, FlywayPreparerCustomizer... customizers) {
        try {
            Arrays.stream(customizers).forEach(customizer -> customizer.before(wrapper));
            return new MigrateFlywayDatabasePreparer(wrapper.getFlyway());
        } finally {
            Arrays.stream(customizers).forEach(customizer -> customizer.after(wrapper));
        }
    }

    private FlywayPreparerCustomizer withLocations(String... locations) {
        return new FlywayPreparerCustomizer() {
            private String[] oldLocations;

            @Override
            public void before(FlywayWrapper wrapper) {
                oldLocations = wrapper.getLocations();
                wrapper.setLocations(locations);
            }

            @Override
            public void after(FlywayWrapper wrapper) {
                wrapper.setLocations(oldLocations);
            }
        };
    }

    private FlywayPreparerCustomizer ignoreMissingMigrations() {
        return new FlywayPreparerCustomizer() {
            private boolean ignoreMissingMigrations;

            @Override
            public void before(FlywayWrapper wrapper) {
                ignoreMissingMigrations = wrapper.getFlyway().isIgnoreMissingMigrations();
                wrapper.getFlyway().setIgnoreMissingMigrations(true);
            }

            @Override
            public void after(FlywayWrapper wrapper) {
                wrapper.getFlyway().setIgnoreMissingMigrations(ignoreMissingMigrations);
            }
        };
    }

    private interface FlywayPreparerCustomizer {

        void before(FlywayWrapper flyway);

        void after(FlywayWrapper flyway);

    }

    private static class FlywayTestExecutionListener {

        public static void dbResetWithAnnotation(Runnable operation) {
            operation.run();
        }
    }
}