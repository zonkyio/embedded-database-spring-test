/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;
import io.zonky.test.category.FlywayTestSuite;
import io.zonky.test.db.context.DatabaseContext;
import io.zonky.test.db.context.DatabaseTargetSource;
import io.zonky.test.db.flyway.preparer.BaselineFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.CleanFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.MigrateFlywayDatabasePreparer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.aop.framework.Advised;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.zonky.test.db.flyway.FlywayDatabaseExtension.FlywayOperation;
import static io.zonky.test.support.TestAssumptions.assumeFlywaySupportsBaselineOperation;
import static io.zonky.test.support.TestAssumptions.assumeFlywaySupportsRepeatableMigrations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@Category(FlywayTestSuite.class)
@RunWith(MockitoJUnitRunner.class)
public class FlywayDatabaseExtensionTest {

    @Mock
    private DatabaseContext databaseContext;

    private Flyway flyway;

    private FlywayWrapper flywayWrapper;

    private FlywayDatabaseExtension extension = new FlywayDatabaseExtension();

    @Before
    public void setUp() throws SQLException {
        Advised dataSource = mock(Advised.class, withSettings().extraInterfaces(DataSource.class));
        when(dataSource.getTargetSource()).thenReturn(new DatabaseTargetSource(databaseContext));
        when(((DataSource) dataSource).getConnection()).thenThrow(SQLException.class);

        FlywayWrapper wrapper = FlywayWrapper.newInstance();
        wrapper.setLocations(ImmutableList.of("db/migration"));
        wrapper.setDataSource((DataSource) dataSource);

        this.flyway = (Flyway) extension.postProcessBeforeInitialization(wrapper.getFlyway(), "flyway");
        this.flywayWrapper = FlywayWrapper.forBean(flyway);
    }

    @Test
    public void cleanFromExecutionListenerShouldBeDeferred() {
        OptimizedFlywayTestExecutionListener.dbResetWithAnnotation(flywayWrapper::clean);

        assertThat(extension.pendingOperations).hasSize(1);
        verifyNoInteractions(databaseContext);
    }

    @Test
    public void baselineFromExecutionListenerShouldBeDeferred() {
        assumeFlywaySupportsBaselineOperation();

        OptimizedFlywayTestExecutionListener.dbResetWithAnnotation(flywayWrapper::baseline);
        assertThat(extension.pendingOperations).hasSize(1);
    }

    @Test
    public void migrateFromExecutionListenerShouldBeDeferred() {
        OptimizedFlywayTestExecutionListener.dbResetWithAnnotation(flywayWrapper::migrate);
        assertThat(extension.pendingOperations).hasSize(1);
    }

    @Test
    public void cleanFromClassicExecutionListenerShouldFail() {
        assertThatCode(() -> FlywayTestExecutionListener.dbResetWithAnnotation(flywayWrapper::clean))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageMatching("Using .* is forbidden, use io.zonky.test.db.flyway.OptimizedFlywayTestExecutionListener instead");

        assertThat(extension.pendingOperations).isEmpty();
        verifyNoInteractions(databaseContext);
    }

    @Test
    public void baselineFromClassicExecutionListenerShouldFail() {
        assumeFlywaySupportsBaselineOperation();

        assertThatCode(() -> FlywayTestExecutionListener.dbResetWithAnnotation(flywayWrapper::baseline))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageMatching("Using .* is forbidden, use io.zonky.test.db.flyway.OptimizedFlywayTestExecutionListener instead");

        assertThat(extension.pendingOperations).isEmpty();
        verifyNoInteractions(databaseContext);
    }

    @Test
    public void migrateFromClassicExecutionListenerShouldFail() {
        assertThatCode(() -> FlywayTestExecutionListener.dbResetWithAnnotation(flywayWrapper::migrate))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageMatching("Using .* is forbidden, use io.zonky.test.db.flyway.OptimizedFlywayTestExecutionListener instead");

        assertThat(extension.pendingOperations).isEmpty();
        verifyNoInteractions(databaseContext);
    }

    @Test
    public void cleanOutsideExecutionListenerShouldBeProcessedImmediately() {
        flywayWrapper.clean();

        assertThat(extension.pendingOperations).isEmpty();
        verify(databaseContext).apply(cleanPreparer(flywayWrapper));
    }

    @Test
    public void baselineOutsideExecutionListenerShouldBeProcessedImmediately() {
        assumeFlywaySupportsBaselineOperation();

        flywayWrapper.baseline();

        assertThat(extension.pendingOperations).isEmpty();
        verify(databaseContext).apply(baselinePreparer(flywayWrapper));
    }

    @Test
    public void migrateOutsideExecutionListenerShouldBeProcessedImmediately() {
        flywayWrapper.migrate();

        assertThat(extension.pendingOperations).isEmpty();
        verify(databaseContext).apply(migratePreparer(flywayWrapper));
    }

    @Test
    public void testResetWithDefaultLocations() {
        extension.pendingOperations.add(migrateOperation(flywayWrapper));
        extension.pendingOperations.add(cleanOperation(flywayWrapper));
        extension.pendingOperations.add(migrateOperation(flywayWrapper));

        extension.processPendingOperations();

        verify(databaseContext).reset();
        verifyNoMoreInteractions(databaseContext);
    }

    @Test
    public void testResetWithAppendableLocation() {
        extension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration")));
        extension.pendingOperations.add(cleanOperation(flywayWrapper));
        extension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration", "db/test_migration/appendable")));

        extension.processPendingOperations();

        InOrder inOrder = inOrder(databaseContext);
        inOrder.verify(databaseContext).reset();

        if (FlywayClassUtils.getFlywayVersion().isGreaterThanOrEqualTo("4.1")) {
            inOrder.verify(databaseContext).apply(migratePreparer(flywayWrapper, withLocations("db/test_migration/appendable"), ignoreMissingMigrations()));
        } else {
            inOrder.verify(databaseContext).apply(migratePreparer(flywayWrapper, withLocations("db/migration", "db/test_migration/appendable")));
        }

        verifyNoMoreInteractions(databaseContext);
    }

    @Test
    public void testResetWithDependentLocations() {
        extension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration")));
        extension.pendingOperations.add(cleanOperation(flywayWrapper));
        extension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration", "db/test_migration/dependent")));

        extension.processPendingOperations();

        InOrder inOrder = inOrder(databaseContext);
        inOrder.verify(databaseContext).reset();
        inOrder.verify(databaseContext).apply(cleanPreparer(flywayWrapper));
        inOrder.verify(databaseContext).apply(migratePreparer(flywayWrapper, withLocations("db/migration", "db/test_migration/dependent")));

        verifyNoMoreInteractions(databaseContext);
    }

    @Test
    public void testResetWithSeparatedLocations() {
        extension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/migration")));
        extension.pendingOperations.add(cleanOperation(flywayWrapper));
        extension.pendingOperations.add(migrateOperation(flywayWrapper, withLocations("db/test_migration/separated")));

        extension.processPendingOperations();

        InOrder inOrder = inOrder(databaseContext);
        inOrder.verify(databaseContext).reset();
        inOrder.verify(databaseContext).apply(cleanPreparer(flywayWrapper));
        inOrder.verify(databaseContext).apply(migratePreparer(flywayWrapper, withLocations("db/test_migration/separated")));

        verifyNoMoreInteractions(databaseContext);
    }

    @Test
    public void squashOperations() {
        FlywayOperation cleanOperation = cleanOperation(flywayWrapper);
        FlywayOperation migrateOperation = migrateOperation(flywayWrapper);

        List<FlywayOperation> operations = ImmutableList.of(
                cleanOperation, migrateOperation, cleanOperation, cleanOperation, migrateOperation);

        assertThat(extension.squashOperations(operations))
                .containsExactly(cleanOperation, migrateOperation);
    }

    @Test
    public void defaultLocations() {
        boolean result = extension.isAppendable(flywayWrapper, ImmutableList.of("classpath:db/migration"));
        assertThat(result).isTrue();
    }

    @Test
    public void appendableLocation() {
        boolean result = extension.isAppendable(flywayWrapper, ImmutableList.of("classpath:db/migration", "classpath:db/test_migration/appendable"));
        assertThat(result).isTrue();
    }

    @Test
    public void dependentLocations() {
        boolean result = extension.isAppendable(flywayWrapper, ImmutableList.of("classpath:db/migration", "classpath:db/test_migration/dependent"));
        assertThat(result).isFalse();
    }

    @Test
    public void separatedLocations() {
        boolean result = extension.isAppendable(flywayWrapper, ImmutableList.of("classpath:db/test_migration/separated"));
        assertThat(result).isFalse();
    }

    @Test
    public void resolveTestLocations() {
        List<String> testLocations = extension.resolveTestLocations(
                flywayWrapper, ImmutableList.of("classpath:db/migration", "classpath:db/test_migration/dependent"));

        assertThat(testLocations).containsExactly("classpath:db/test_migration/dependent");
    }

    @Test
    public void findFirstVersion() {
        MigrationVersion firstVersion = extension.findFirstVersion(
                flywayWrapper, ImmutableList.of("db/test_migration/dependent"));

        assertThat(firstVersion).isNotNull();
        assertThat(firstVersion.getVersion()).isEqualTo("0001.2");
    }

    @Test
    public void findLastVersion() {
        MigrationVersion firstVersion = extension.findLastVersion(
                flywayWrapper, ImmutableList.of("db/test_migration/dependent"));

        assertThat(firstVersion).isNotNull();
        assertThat(firstVersion.getVersion()).isEqualTo("0999.1");
    }

    @Test
    public void resolveMigrations() {
        assumeFlywaySupportsRepeatableMigrations();

        Collection<ResolvedMigration> resolvedMigrations = extension.resolveMigrations(
                flywayWrapper, ImmutableList.of("db/migration", "db/test_migration/dependent"));

        assertThat(resolvedMigrations).extracting("script")
                .containsExactly(
                        "V0001_1__create_person_table.sql",
                        "V0001_2__add_full_name_column.sql",
                        "V0002_1__rename_surname_column.sql",
                        "V0999_1__create_test_data.sql",
                        "R__people_view.sql");
    }

    private static FlywayOperation cleanOperation(FlywayWrapper wrapper) {
        return new FlywayOperation(wrapper, cleanPreparer(wrapper));
    }

    private static FlywayOperation migrateOperation(FlywayWrapper wrapper, FlywayPreparerCustomizer... customizers) {
        return new FlywayOperation(wrapper, migratePreparer(wrapper, customizers));
    }

    private static CleanFlywayDatabasePreparer cleanPreparer(FlywayWrapper wrapper) {
        return new CleanFlywayDatabasePreparer(FlywayDescriptor.from(wrapper));
    }

    private static BaselineFlywayDatabasePreparer baselinePreparer(FlywayWrapper wrapper) {
        return new BaselineFlywayDatabasePreparer(FlywayDescriptor.from(wrapper));
    }

    private static MigrateFlywayDatabasePreparer migratePreparer(FlywayWrapper wrapper, FlywayPreparerCustomizer... customizers) {
        try {
            Arrays.stream(customizers).forEach(customizer -> customizer.before(wrapper));
            return new MigrateFlywayDatabasePreparer(FlywayDescriptor.from(wrapper));
        } finally {
            Arrays.stream(customizers).forEach(customizer -> customizer.after(wrapper));
        }
    }

    private FlywayPreparerCustomizer withLocations(String... locations) {
        return new FlywayPreparerCustomizer() {
            private List<String> oldLocations;

            @Override
            public void before(FlywayWrapper wrapper) {
                oldLocations = wrapper.getLocations();
                wrapper.setLocations(ImmutableList.copyOf(locations));
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
                ignoreMissingMigrations = wrapper.isIgnoreMissingMigrations();
                wrapper.setIgnoreMissingMigrations(true);
            }

            @Override
            public void after(FlywayWrapper wrapper) {
                wrapper.setIgnoreMissingMigrations(ignoreMissingMigrations);
            }
        };
    }

    private interface FlywayPreparerCustomizer {

        void before(FlywayWrapper flyway);

        void after(FlywayWrapper flyway);

    }

    private static class OptimizedFlywayTestExecutionListener {

        public static void dbResetWithAnnotation(Runnable operation) {
            operation.run();
        }
    }

    private static class FlywayTestExecutionListener {

        public static void dbResetWithAnnotation(Runnable operation) {
            operation.run();
        }
    }
}
