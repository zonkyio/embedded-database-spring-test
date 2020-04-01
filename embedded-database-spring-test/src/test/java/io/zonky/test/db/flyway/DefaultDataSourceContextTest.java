package io.zonky.test.db.flyway;

import io.zonky.test.db.preparer.RecordingDataSource;
import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.config.DatabaseProviders;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultDataSourceContextTest {

    @Mock
    private DatabaseProviders databaseProviders;

    @Spy
    @InjectMocks
    private DefaultDataSourceContext dataSourceContext;

    @Test
    public void getTargetClass() {
        assertThat(dataSourceContext.getTargetClass()).isEqualTo(DataSource.class);
    }

    @Test
    public void isStatic() {
        assertThat(dataSourceContext.isStatic()).isFalse();
    }

    @Test
    public void databaseDescriptorMustBeSet() {
        assertThatCode(() -> dataSourceContext.apply(dataSource -> {}))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("database descriptor must be set");
    }

    @Test
    public void dataSourceContextMustBeInitialized() {
        DatabaseProvider databaseProvider = mock(DatabaseProvider.class);
        when(databaseProviders.getProvider(any())).thenReturn(databaseProvider);

        dataSourceContext.setDescriptor(new DatabaseDescriptor("database", "provider"));

        assertThatCode(() -> dataSourceContext.reset())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("data source context must be initialized");
    }

    @Test
    public void testPreparers() throws Exception {
        DatabaseProvider databaseProvider = mock(DatabaseProvider.class);
        when(databaseProviders.getProvider(any())).thenReturn(databaseProvider);
        dataSourceContext.setDescriptor(new DatabaseDescriptor("database", "provider"));

        DatabasePreparer preparer1 = mock(DatabasePreparer.class);
        DatabasePreparer preparer2 = mock(DatabasePreparer.class);
        DatabasePreparer preparer3 = mock(DatabasePreparer.class);
        DatabasePreparer preparer4 = mock(DatabasePreparer.class);
        DatabasePreparer preparer5 = mock(DatabasePreparer.class);

        dataSourceContext.apply(preparer1);
        dataSourceContext.apply(preparer2);

        dataSourceContext.onApplicationEvent(null);

        dataSourceContext.apply(preparer3);
        dataSourceContext.apply(preparer4);
        dataSourceContext.getTarget();

        dataSourceContext.apply(preparer5);

        dataSourceContext.reset();
        dataSourceContext.getTarget();

        InOrder inOrder = inOrder(dataSourceContext, databaseProvider, preparer5);
        inOrder.verify(dataSourceContext).setDescriptor(any());
        inOrder.verify(dataSourceContext).apply(preparer1);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1)));
        inOrder.verify(dataSourceContext).apply(preparer2);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)));
        inOrder.verify(dataSourceContext).apply(preparer3);
        inOrder.verify(dataSourceContext).apply(preparer4);
        inOrder.verify(dataSourceContext).getTarget();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3, preparer4)));
        inOrder.verify(dataSourceContext).apply(preparer5);
        inOrder.verify(preparer5).prepare(any());
        inOrder.verify(dataSourceContext).reset();
        inOrder.verify(dataSourceContext).getTarget();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)));

        verifyNoMoreInteractions(databaseProvider);
    }

    @Test
    public void testRecording() throws Exception {
        DatabaseProvider databaseProvider = mock(DatabaseProvider.class);
        when(databaseProviders.getProvider(any())).thenReturn(databaseProvider);
        when(databaseProvider.createDatabase(any())).thenReturn(mock(EmbeddedDatabase.class, RETURNS_MOCKS));
        dataSourceContext.setDescriptor(new DatabaseDescriptor("database", "provider"));

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

        manualOperations.accept((DataSource) dataSourceContext.getTarget());

        dataSourceContext.apply(preparer1);

        manualOperations.accept((DataSource) dataSourceContext.getTarget());

        dataSourceContext.onApplicationEvent(null);

        manualOperations.accept((DataSource) dataSourceContext.getTarget());

        dataSourceContext.reset();
        dataSourceContext.getTarget();

        InOrder inOrder = inOrder(dataSourceContext, databaseProvider);
        inOrder.verify(dataSourceContext).setDescriptor(any());
        inOrder.verify(dataSourceContext).getTarget();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of()));
        inOrder.verify(dataSourceContext).apply(preparer1);
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(recordedPreparer, preparer1)));
        inOrder.verify(dataSourceContext).getTarget();
        inOrder.verify(dataSourceContext).onApplicationEvent(null);
        inOrder.verify(dataSourceContext).getTarget();
        inOrder.verify(dataSourceContext).reset();
        inOrder.verify(dataSourceContext).getTarget();
        inOrder.verify(databaseProvider).createDatabase(new CompositeDatabasePreparer(ImmutableList.of(recordedPreparer, preparer1, recordedPreparer)));

        verifyNoMoreInteractions(databaseProvider);
    }
}