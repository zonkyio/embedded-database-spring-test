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

import static io.zonky.test.db.context.DataSourceContext.ContextState.DIRTY;
import static io.zonky.test.db.context.DataSourceContext.ContextState.FRESH;
import static io.zonky.test.db.context.DataSourceContext.ContextState.INITIALIZING;
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
public class DefaultDataSourceContextTest {

    private static Method MOCK_TEST_METHOD;

    @Mock
    private DatabaseProvider databaseProvider;

    @Spy
    @InjectMocks
    private DefaultDataSourceContext dataSourceContext;

    @BeforeClass
    public static void beforeClass() {
        MOCK_TEST_METHOD = ReflectionUtils.findMethod(DefaultDataSourceContextTest.class, "beforeClass");
    }

    @Test
    public void dataSourceContextMustBeInitializedBeforeReset() {
        assertThatCode(() -> dataSourceContext.reset())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Data source context must be initialized");
    }

    @Test
    public void dataSourceContextInInitializingStateShouldCreateDatabaseWhenNecessary() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class));

        assertThat(dataSourceContext.getState()).isEqualTo(INITIALIZING);
        assertThat(dataSourceContext.getDatabase()).isNotNull();

        verify(databaseProvider).createDatabase(any());
    }

    @Test
    public void dataSourceContextInTestPreparationStateShouldCreateDatabaseWhenInvokedFromMainThread() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class));

        dataSourceContext.handleContextRefreshed(null);

        assertThat(dataSourceContext.getState()).isEqualTo(FRESH);
        assertThat(dataSourceContext.getDatabase()).isNotNull();

        verify(databaseProvider).createDatabase(any());
    }

    @Test
    public void dataSourceContextInTestPreparationStateShouldCreateDatabaseWhenPreviousDatabaseIsNotAvailableEvenWhenInvokedOutOfMainThread() throws InterruptedException {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class));

        runInDifferentThread(() -> dataSourceContext.handleContextRefreshed(null));

        assertThat(dataSourceContext.getState()).isEqualTo(FRESH);
        assertThat(dataSourceContext.getDatabase()).isNotNull();

        verify(databaseProvider).createDatabase(any());
    }

    @Test
    public void dataSourceContextInTestExecutionStateShouldCreateDatabaseWhenTestExecutionStarted() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class));

        dataSourceContext.handleContextRefreshed(null);
        dataSourceContext.handleTestStarted(new TestExecutionStartedEvent(this, MOCK_TEST_METHOD));

        verify(databaseProvider).createDatabase(any());

        assertThat(dataSourceContext.getState()).isEqualTo(FRESH);
        assertThat(dataSourceContext.getDatabase()).isNotNull();

        verifyNoMoreInteractions(databaseProvider);
    }

    @Test
    public void dataSourceContextInTestPreparationStateShouldCreateDatabaseWhenResetAndInvokedFromMainThread() {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class), mock(EmbeddedDatabase.class));

        dataSourceContext.handleContextRefreshed(null);
        dataSourceContext.handleTestStarted(new TestExecutionStartedEvent(this, MOCK_TEST_METHOD));

        EmbeddedDatabase database = dataSourceContext.getDatabase();
        dataSourceContext.handleTestFinished(new TestExecutionFinishedEvent(this, MOCK_TEST_METHOD));
        assertThat(dataSourceContext.getState()).isEqualTo(DIRTY);
        dataSourceContext.reset();

        assertThat(dataSourceContext.getState()).isEqualTo(FRESH);
        assertThat(dataSourceContext.getDatabase()).isNotSameAs(database);

        verify(databaseProvider, times(2)).createDatabase(any());
    }

    @Test
    public void dataSourceContextInTestPreparationStateShouldReturnPreviousDatabaseWhenResetAndInvokedOutOfMainThread() throws InterruptedException {
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class), mock(EmbeddedDatabase.class));

        runInDifferentThread(() -> dataSourceContext.handleContextRefreshed(null));

        dataSourceContext.handleTestStarted(new TestExecutionStartedEvent(this, MOCK_TEST_METHOD));
        verify(databaseProvider).createDatabase(any());

        EmbeddedDatabase database = dataSourceContext.getDatabase();
        dataSourceContext.handleTestFinished(new TestExecutionFinishedEvent(this, MOCK_TEST_METHOD));
        assertThat(dataSourceContext.getState()).isEqualTo(DIRTY);
        dataSourceContext.reset();

        assertThat(dataSourceContext.getState()).isEqualTo(FRESH);
        assertThat(dataSourceContext.getDatabase()).isSameAs(database);

        verifyNoMoreInteractions(databaseProvider);
    }

    @Test
    public void testPreparers() throws Exception {
        DatabasePreparer preparer1 = mock(DatabasePreparer.class);
        DatabasePreparer preparer2 = mock(DatabasePreparer.class);
        DatabasePreparer preparer3 = mock(DatabasePreparer.class);
        DatabasePreparer preparer4 = mock(DatabasePreparer.class);
        DatabasePreparer preparer5 = mock(DatabasePreparer.class);

        dataSourceContext.apply(preparer1);
        dataSourceContext.apply(preparer2);

        dataSourceContext.handleContextRefreshed(null);

        dataSourceContext.apply(preparer3);
        dataSourceContext.apply(preparer4);
        dataSourceContext.getDatabase();

        dataSourceContext.apply(preparer5);

        dataSourceContext.reset();
        dataSourceContext.getDatabase();

        InOrder inOrder = inOrder(dataSourceContext, databaseProvider, preparer5);
        inOrder.verify(dataSourceContext).apply(preparer1);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1)));
        inOrder.verify(dataSourceContext).apply(preparer2);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)));
        inOrder.verify(dataSourceContext).apply(preparer3);
        inOrder.verify(dataSourceContext).apply(preparer4);
        inOrder.verify(dataSourceContext).getDatabase();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3, preparer4)));
        inOrder.verify(dataSourceContext).apply(preparer5);
        inOrder.verify(preparer5).prepare(any());
        inOrder.verify(dataSourceContext).reset();
        inOrder.verify(dataSourceContext).getDatabase();
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

        manualOperations.accept(dataSourceContext.getDatabase());

        dataSourceContext.apply(preparer1);

        manualOperations.accept(dataSourceContext.getDatabase());

        dataSourceContext.handleContextRefreshed(null);

        manualOperations.accept(dataSourceContext.getDatabase());

        dataSourceContext.reset();
        dataSourceContext.getDatabase();

        InOrder inOrder = inOrder(dataSourceContext, databaseProvider);
        inOrder.verify(dataSourceContext).getDatabase();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of()));
        inOrder.verify(dataSourceContext).apply(preparer1);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(recordedPreparer, preparer1)));
        inOrder.verify(dataSourceContext).getDatabase();
        inOrder.verify(dataSourceContext).handleContextRefreshed(null);
        inOrder.verify(dataSourceContext).getDatabase();
        inOrder.verify(dataSourceContext).reset();
        inOrder.verify(dataSourceContext).getDatabase();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(recordedPreparer, preparer1, recordedPreparer)));

        verifyNoMoreInteractions(databaseProvider);
    }

    private static void runInDifferentThread(Runnable runnable) throws InterruptedException {
        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();
    }
}