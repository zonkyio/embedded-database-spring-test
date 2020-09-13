package io.zonky.test.db.context;

import io.zonky.test.db.event.TestExecutionFinishedEvent;
import io.zonky.test.db.event.TestExecutionStartedEvent;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.preparer.RecordingDataSource;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

import static io.zonky.test.db.context.DatabaseContext.ContextState.DIRTY;
import static io.zonky.test.db.context.DatabaseContext.ContextState.FRESH;
import static io.zonky.test.db.context.DatabaseContext.ContextState.INITIALIZING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultDatabaseContextTest {

    private static Method MOCK_TEST_METHOD;

    @Mock
    private DatabaseProvider databaseProvider;

    @Spy
    @InjectMocks
    private DefaultDatabaseContext databaseContext;

    @BeforeClass
    public static void beforeClass() {
        MOCK_TEST_METHOD = ReflectionUtils.findMethod(DefaultDatabaseContextTest.class, "beforeClass");
    }

    @Test
    public void databaseContextMustBeInitializedBeforeReset() {
        assertThatCode(() -> databaseContext.reset())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Data source context must be initialized");
    }

    @Test
    public void databaseContextInInitializingStateShouldCreateDatabaseWhenNecessary() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class));

        assertThat(databaseContext.getState()).isEqualTo(INITIALIZING);
        assertThat(databaseContext.getDatabase()).isNotNull();

        verify(databaseProvider).createDatabase(any());
    }

    @Test
    public void databaseContextInTestPreparationStateShouldCreateDatabaseWhenInvokedFromMainThread() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class));

        databaseContext.handleContextRefreshed(null);

        assertThat(databaseContext.getState()).isEqualTo(FRESH);
        assertThat(databaseContext.getDatabase()).isNotNull();

        verify(databaseProvider).createDatabase(any());
    }

    @Test
    public void databaseContextInTestPreparationStateShouldCreateDatabaseWhenPreviousDatabaseIsNotAvailableEvenWhenInvokedOutOfMainThread() throws InterruptedException {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class));

        runInDifferentThread(() -> databaseContext.handleContextRefreshed(null));

        assertThat(databaseContext.getState()).isEqualTo(FRESH);
        assertThat(databaseContext.getDatabase()).isNotNull();

        verify(databaseProvider).createDatabase(any());
    }

    @Test
    public void databaseContextInTestExecutionStateShouldCreateDatabaseWhenTestExecutionStarted() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class));

        databaseContext.handleContextRefreshed(null);
        databaseContext.handleTestStarted(new TestExecutionStartedEvent(this, MOCK_TEST_METHOD));

        verify(databaseProvider).createDatabase(any());

        assertThat(databaseContext.getState()).isEqualTo(FRESH);
        assertThat(databaseContext.getDatabase()).isNotNull();

        verifyNoMoreInteractions(databaseProvider);
    }

    @Test
    public void databaseContextInTestPreparationStateShouldCreateDatabaseWhenResetAndInvokedFromMainThread() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class), mock(EmbeddedDatabase.class));

        databaseContext.handleContextRefreshed(null);
        databaseContext.handleTestStarted(new TestExecutionStartedEvent(this, MOCK_TEST_METHOD));

        EmbeddedDatabase database = databaseContext.getDatabase();
        databaseContext.handleTestFinished(new TestExecutionFinishedEvent(this, MOCK_TEST_METHOD));
        assertThat(databaseContext.getState()).isEqualTo(DIRTY);
        databaseContext.reset();

        assertThat(databaseContext.getState()).isEqualTo(FRESH);
        assertThat(databaseContext.getDatabase()).isNotSameAs(database);

        verify(databaseProvider, times(2)).createDatabase(any());
    }

    @Test
    public void databaseContextInTestPreparationStateShouldReturnPreviousDatabaseWhenResetAndInvokedOutOfMainThread() throws InterruptedException {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class), mock(EmbeddedDatabase.class));

        runInDifferentThread(() -> databaseContext.handleContextRefreshed(null));

        databaseContext.handleTestStarted(new TestExecutionStartedEvent(this, MOCK_TEST_METHOD));
        verify(databaseProvider).createDatabase(any());

        EmbeddedDatabase database = databaseContext.getDatabase();
        databaseContext.handleTestFinished(new TestExecutionFinishedEvent(this, MOCK_TEST_METHOD));
        assertThat(databaseContext.getState()).isEqualTo(DIRTY);
        databaseContext.reset();

        assertThat(databaseContext.getState()).isEqualTo(FRESH);
        assertThat(databaseContext.getDatabase()).isSameAs(database);

        verifyNoMoreInteractions(databaseProvider);
    }

    @Test
    public void testPreparers() throws Exception {
        DatabasePreparer preparer1 = mock(DatabasePreparer.class);
        DatabasePreparer preparer2 = mock(DatabasePreparer.class);
        DatabasePreparer preparer3 = mock(DatabasePreparer.class);
        DatabasePreparer preparer4 = mock(DatabasePreparer.class);
        DatabasePreparer preparer5 = mock(DatabasePreparer.class);

        databaseContext.apply(preparer1);
        databaseContext.apply(preparer2);

        databaseContext.handleContextRefreshed(null);

        databaseContext.apply(preparer3);
        databaseContext.apply(preparer4);
        databaseContext.getDatabase();

        databaseContext.apply(preparer5);

        databaseContext.reset();
        databaseContext.getDatabase();

        InOrder inOrder = inOrder(databaseContext, databaseProvider, preparer5);
        inOrder.verify(databaseContext).apply(preparer1);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1)));
        inOrder.verify(databaseContext).apply(preparer2);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)));
        inOrder.verify(databaseContext).apply(preparer3);
        inOrder.verify(databaseContext).apply(preparer4);
        inOrder.verify(databaseContext).getDatabase();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3, preparer4)));
        inOrder.verify(databaseContext).apply(preparer5);
        inOrder.verify(preparer5).prepare(any());
        inOrder.verify(databaseContext).reset();
        inOrder.verify(databaseContext).getDatabase();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)));

        verifyNoMoreInteractions(databaseProvider);
    }

    @Test
    public void testRecording() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class, RETURNS_MOCKS));

        DatabasePreparer preparer1 = mock(DatabasePreparer.class);

        Consumer<DataSource> manualOperations = dataSource -> {
            try {
                Connection connection = dataSource.getConnection();
                connection.commit();
                connection.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        };

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));
        manualOperations.accept(recordingDataSource);
        DatabasePreparer recordedPreparer = recordingDataSource.getPreparer();

        manualOperations.accept(databaseContext.getDatabase());

        databaseContext.apply(preparer1);

        manualOperations.accept(databaseContext.getDatabase());

        databaseContext.handleContextRefreshed(null);

        manualOperations.accept(databaseContext.getDatabase());

        databaseContext.reset();
        databaseContext.getDatabase();

        InOrder inOrder = inOrder(databaseContext, databaseProvider);
        inOrder.verify(databaseContext).getDatabase();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of()));
        inOrder.verify(databaseContext).apply(preparer1);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(recordedPreparer, preparer1)));
        inOrder.verify(databaseContext).getDatabase();
        inOrder.verify(databaseContext).handleContextRefreshed(null);
        inOrder.verify(databaseContext).getDatabase();
        inOrder.verify(databaseContext).reset();
        inOrder.verify(databaseContext).getDatabase();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(recordedPreparer, preparer1, recordedPreparer)));

        verifyNoMoreInteractions(databaseProvider);
    }

    private static void runInDifferentThread(Runnable runnable) throws InterruptedException {
        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();
    }
}