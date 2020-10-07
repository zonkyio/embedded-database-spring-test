package io.zonky.test.db.provider;

import com.google.common.collect.ImmutableList;
import io.zonky.test.db.flyway.FlywayDescriptor;
import io.zonky.test.db.flyway.FlywayWrapper;
import io.zonky.test.db.flyway.preparer.BaselineFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.CleanFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.MigrateFlywayDatabasePreparer;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.common.OptimizingDatabaseProvider;
import io.zonky.test.db.support.TestDatabasePreparer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class OptimizingDatabaseProviderTest {

    @Mock
    private DatabaseProvider targetProvider;

    @InjectMocks
    private OptimizingDatabaseProvider optimizingProvider;

    @Test
    public void noFlywayPreparer() {
        DatabasePreparer preparer = TestDatabasePreparer.empty();
        optimizingProvider.createDatabase(preparer);
        verify(targetProvider).createDatabase(preparer);
    }

    @Test
    public void noFlywayPreparers() {
        CompositeDatabasePreparer preparer = preparers(TestDatabasePreparer.empty());
        optimizingProvider.createDatabase(preparer);
        verify(targetProvider).createDatabase(preparer);
    }

    @Test
    public void flywayMigrationOnly() {
        optimizingProvider.createDatabase(preparers(
                migrate("test")));

        verify(targetProvider).createDatabase(preparers(
                migrate("test")));
    }

    @Test
    public void flywayMigrationFollowedByClean() {
        optimizingProvider.createDatabase(preparers(
                migrate("test"),
                clean("test")));

        verify(targetProvider).createDatabase(preparers(
                clean("test")));
    }

    @Test
    public void redundantFlywayMigration() {
        optimizingProvider.createDatabase(preparers(
                migrate("test"),
                clean("test"),
                migrate("test")));

        verify(targetProvider).createDatabase(preparers(
                clean("test"),
                migrate("test")));
    }

    @Test
    public void repeatFlywayClean() {
        optimizingProvider.createDatabase(preparers(
                migrate("test"),
                clean("test"),
                migrate("test"),
                clean("test"),
                migrate("test")));

        verify(targetProvider).createDatabase(preparers(
                clean("test"),
                migrate("test")));
    }

    @Test
    public void duplicateFlywayMigrations() {
        optimizingProvider.createDatabase(preparers(
                migrate("test"),
                migrate("test"),
                clean("test"),
                migrate("test"),
                migrate("test")));

        verify(targetProvider).createDatabase(preparers(
                clean("test"),
                migrate("test"),
                migrate("test")));
    }

    @Test
    public void baselineFlywayMigration() {
        optimizingProvider.createDatabase(preparers(
                migrate("test"),
                baseline("test"),
                clean("test"),
                migrate("test")));

        verify(targetProvider).createDatabase(preparers(
                migrate("test"),
                baseline("test"),
                clean("test"),
                migrate("test")));
    }

    @Test
    public void multipleFlywaySchemas() {
        optimizingProvider.createDatabase(preparers(
                migrate("test1"),
                migrate("test2"),
                clean("test1"),
                migrate("test1")));

        verify(targetProvider).createDatabase(preparers(
                migrate("test1"),
                migrate("test2"),
                clean("test1"),
                migrate("test1")));
    }

    @Test
    public void flywayMigrationWithOtherGeneralPreparers() {
        optimizingProvider.createDatabase(preparers(
                preparer("before"),
                migrate("test"),
                preparer("after")));

        verify(targetProvider).createDatabase(preparers(
                preparer("before"),
                migrate("test"),
                preparer("after")));
    }

    @Test
    public void flywayMigrationFollowedByCleanWithOtherGeneralPreparers() {
        optimizingProvider.createDatabase(preparers(
                preparer("before"),
                migrate("test"),
                preparer("after"),
                clean("test")));

        verify(targetProvider).createDatabase(preparers(
                preparer("before"),
                preparer("after"),
                clean("test")));
    }

    @Test
    public void redundantFlywayMigrationWithOtherGeneralPreparers() {
        optimizingProvider.createDatabase(preparers(
                preparer("before"),
                migrate("test"),
                preparer("after"),
                clean("test"),
                migrate("test")));

        verify(targetProvider).createDatabase(preparers(
                preparer("before"),
                preparer("after"),
                clean("test"),
                migrate("test")));
    }

    @Test
    public void repeatFlywayCleanWithOtherGeneralPreparers() {
        optimizingProvider.createDatabase(preparers(
                preparer("before"),
                migrate("test"),
                preparer("middle"),
                clean("test"),
                migrate("test"),
                preparer("after"),
                clean("test"),
                migrate("test")));

        verify(targetProvider).createDatabase(preparers(
                preparer("before"),
                preparer("middle"),
                preparer("after"),
                clean("test"),
                migrate("test")));
    }

    @Test
    public void duplicateFlywayMigrationsWithOtherGeneralPreparers() {
        optimizingProvider.createDatabase(preparers(
                preparer("before"),
                migrate("test"),
                migrate("test"),
                preparer("after"),
                clean("test"),
                migrate("test"),
                migrate("test")));

        verify(targetProvider).createDatabase(preparers(
                preparer("before"),
                preparer("after"),
                clean("test"),
                migrate("test"),
                migrate("test")));
    }

    private static CompositeDatabasePreparer preparers(DatabasePreparer... preparers) {
        return new CompositeDatabasePreparer(ImmutableList.copyOf(preparers));
    }

    private static MigrateFlywayDatabasePreparer migrate(String... schemas) {
        FlywayWrapper wrapper = FlywayWrapper.newInstance();
        wrapper.setSchemas(ImmutableList.copyOf(schemas));
        return new MigrateFlywayDatabasePreparer(FlywayDescriptor.from(wrapper));
    }

    private static CleanFlywayDatabasePreparer clean(String... schemas) {
        FlywayWrapper wrapper = FlywayWrapper.newInstance();
        wrapper.setSchemas(ImmutableList.copyOf(schemas));
        return new CleanFlywayDatabasePreparer(FlywayDescriptor.from(wrapper));
    }

    private static BaselineFlywayDatabasePreparer baseline(String... schemas) {
        FlywayWrapper wrapper = FlywayWrapper.newInstance();
        wrapper.setSchemas(ImmutableList.copyOf(schemas));
        return new BaselineFlywayDatabasePreparer(FlywayDescriptor.from(wrapper));
    }

    private static DatabasePreparer preparer(String name) {
        return TestDatabasePreparer.empty(name);
    }
}