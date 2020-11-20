package io.zonky.test.db.provider;

import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.common.OptimizingDatabaseProvider;
import io.zonky.test.db.support.TestDatabasePreparer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class OptimizingDatabaseProviderTest {

    @Mock
    private DatabaseProvider targetProvider;

    @InjectMocks
    private OptimizingDatabaseProvider optimizingProvider;

    @Test
    public void newBaseline() {
        DatabasePreparer preparer1 = TestDatabasePreparer.empty("A-preparer1");
        CompositeDatabasePreparer compositePreparer = preparers(preparer1);

        optimizingProvider.createDatabase(compositePreparer);

        verify(targetProvider).createDatabase(compositePreparer);
    }

    @Test
    public void existingBaseline() {
        optimizingProvider.createDatabase(preparers(preparer("1_main")));
        optimizingProvider.createDatabase(preparers(preparer("1_main")));

        verify(targetProvider, times(2)).createDatabase(preparers(preparer("1_main")));
    }

    @Test
    public void complementaryPreparer() {
        AtomicInteger counter = new AtomicInteger();

        optimizingProvider.createDatabase(preparers(preparer("2_main")));
        optimizingProvider.createDatabase(preparers(preparer("2_main"), preparer("2_test", counter)));

        verify(targetProvider, times(2)).createDatabase(preparers(preparer("2_main")));

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    public void repeatingComplementaryPreparer() {
        AtomicInteger counter = new AtomicInteger();

        optimizingProvider.createDatabase(preparers(preparer("3_main")));
        optimizingProvider.createDatabase(preparers(preparer("3_main"), preparer("3_test", counter)));
        optimizingProvider.createDatabase(preparers(preparer("3_main"), preparer("3_test", counter)));
        optimizingProvider.createDatabase(preparers(preparer("3_main"), preparer("3_test", counter)));

        verify(targetProvider, times(3)).createDatabase(preparers(preparer("3_main")));
        verify(targetProvider).createDatabase(preparers(preparer("3_main"), preparer("3_test", counter)));

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    public void slowOperation() {
        AtomicInteger counter = new AtomicInteger();

        optimizingProvider.createDatabase(preparers(preparer("4_main")));
        optimizingProvider.createDatabase(preparers(preparer("4_main"), slowPreparer("4_test", counter)));

        verify(targetProvider).createDatabase(preparers(preparer("4_main")));
        verify(targetProvider).createDatabase(preparers(preparer("4_main"), slowPreparer("4_test", counter)));

        assertThat(counter.get()).isEqualTo(0);
    }

    private static CompositeDatabasePreparer preparers(DatabasePreparer... preparers) {
        return new CompositeDatabasePreparer(com.google.common.collect.ImmutableList.copyOf(preparers));
    }

    private static DatabasePreparer preparer(String name) {
        return TestDatabasePreparer.empty(name);
    }

    private static DatabasePreparer preparer(String name, AtomicInteger counter) {
        return TestDatabasePreparer.of(name, x -> counter.incrementAndGet());
    }

    private static DatabasePreparer slowPreparer(String name, AtomicInteger counter) {
        return TestDatabasePreparer.of(name, 1000, x -> counter.incrementAndGet());
    }
}