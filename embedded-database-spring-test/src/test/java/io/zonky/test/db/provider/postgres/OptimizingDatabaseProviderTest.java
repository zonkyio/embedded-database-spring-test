package io.zonky.test.db.provider.postgres;

import com.google.common.collect.ImmutableList;
import io.zonky.test.db.context.DataSourceContext;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseRequest;
import io.zonky.test.db.provider.DatabaseTemplate;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static io.zonky.test.db.context.DataSourceContext.State.FRESH;
import static io.zonky.test.db.context.DataSourceContext.State.INITIALIZING;
import static io.zonky.test.db.provider.postgres.OptimizingDatabaseProvider.EMPTY_PREPARER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OptimizingDatabaseProviderTest {

    @Mock
    private DataSourceContext mockContext;
    @Mock
    private TemplatableDatabaseProvider mockProvider;

    private OptimizingDatabaseProvider optimizingProvider;

    @Before
    public void setUp() {
        optimizingProvider = new OptimizingDatabaseProvider(mockProvider, ImmutableList.of(mockContext));
    }

    @Test
    public void createNewTemplate() {
        DatabasePreparer preparer = dataSource -> {};
        DatabaseTemplate template = new DatabaseTemplate("template");
        EmbeddedDatabase database = mock(EmbeddedDatabase.class);

        when(mockProvider.createTemplate(any())).thenReturn(template);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenReturn(database);

        assertThat(optimizingProvider.createDatabase(preparer)).isSameAs(database);

        InOrder inOrder = inOrder(mockProvider);
        inOrder.verify(mockProvider).createTemplate(DatabaseRequest.of(new CompositeDatabasePreparer(ImmutableList.of(preparer))));
        inOrder.verify(mockProvider).createDatabase(DatabaseRequest.of(EMPTY_PREPARER, template));

        verify(mockContext, never()).getState();
    }

    @Test
    public void reuseExistingTemplate() {
        DatabasePreparer preparer = dataSource -> {};
        DatabaseTemplate template = new DatabaseTemplate("template");

        EmbeddedDatabase database1 = mock(EmbeddedDatabase.class);
        EmbeddedDatabase database2 = mock(EmbeddedDatabase.class);
        EmbeddedDatabase database3 = mock(EmbeddedDatabase.class);

        when(mockProvider.createTemplate(any())).thenReturn(template);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenReturn(database1, database2, database3);

        assertThat(optimizingProvider.createDatabase(preparer)).isSameAs(database1);
        assertThat(optimizingProvider.createDatabase(preparer)).isSameAs(database2);
        assertThat(optimizingProvider.createDatabase(preparer)).isSameAs(database3);

        InOrder inOrder = inOrder(mockProvider);
        inOrder.verify(mockProvider).createTemplate(DatabaseRequest.of(new CompositeDatabasePreparer(ImmutableList.of(preparer))));
        inOrder.verify(mockProvider, times(3)).createDatabase(DatabaseRequest.of(EMPTY_PREPARER, template));

        verify(mockContext, never()).getState();
    }

    @Test
    public void reuseClosestTemplate() {
        DatabaseTemplate template = new DatabaseTemplate("template");

        DatabasePreparer preparer1 = dataSource -> {};
        DatabasePreparer preparer2 = dataSource -> {};
        DatabasePreparer preparer3 = dataSource -> {};

        EmbeddedDatabase database1 = mock(EmbeddedDatabase.class);
        EmbeddedDatabase database2 = mock(EmbeddedDatabase.class);

        when(mockContext.getState()).thenReturn(FRESH);
        when(mockProvider.createTemplate(any())).thenReturn(template);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenReturn(database1, database2);

        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)))).isSameAs(database1);
        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3)))).isSameAs(database2);

        InOrder inOrder = inOrder(mockContext, mockProvider);
        inOrder.verify(mockProvider).createTemplate(DatabaseRequest.of(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2))));
        inOrder.verify(mockProvider).createDatabase(DatabaseRequest.of(EMPTY_PREPARER, template));
        inOrder.verify(mockContext).getState();
        inOrder.verify(mockProvider).createDatabase(DatabaseRequest.of(new CompositeDatabasePreparer(ImmutableList.of(preparer3)), template));
    }

    @Test
    public void createNewSpecificTemplate() {
        DatabaseTemplate template1 = new DatabaseTemplate("template1");
        DatabaseTemplate template2 = new DatabaseTemplate("template2");

        DatabasePreparer preparer1 = dataSource -> {};
        DatabasePreparer preparer2 = dataSource -> {};
        DatabasePreparer preparer3 = dataSource -> {};

        EmbeddedDatabase database1 = mock(EmbeddedDatabase.class);
        EmbeddedDatabase database2 = mock(EmbeddedDatabase.class);

        when(mockContext.getState()).thenReturn(INITIALIZING);
        when(mockProvider.createTemplate(any())).thenReturn(template1, template2);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenReturn(database1, database2);

        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)))).isSameAs(database1);
        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3)))).isSameAs(database2);
        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3)))).isSameAs(database2);

        InOrder inOrder = inOrder(mockContext, mockProvider);
        inOrder.verify(mockProvider).createTemplate(DatabaseRequest.of(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2))));
        inOrder.verify(mockProvider).createDatabase(DatabaseRequest.of(EMPTY_PREPARER, template1));
        inOrder.verify(mockContext).getState();
        inOrder.verify(mockProvider).createTemplate(DatabaseRequest.of(new CompositeDatabasePreparer(ImmutableList.of(preparer3)), template1));
        inOrder.verify(mockProvider, times(2)).createDatabase(DatabaseRequest.of(EMPTY_PREPARER, template2));
    }

    @Test
    public void creatingTemplateShouldThrowProviderException() {
        DatabasePreparer preparer = dataSource -> {};

        when(mockProvider.createTemplate(any())).thenThrow(new ProviderException("test exception"));

        assertThatCode(() -> optimizingProvider.createDatabase(preparer))
                .isExactlyInstanceOf(ProviderException.class)
                .hasMessage("test exception");
    }
    @Test
    public void creatingDatabaseShouldThrowProviderException() {
        DatabasePreparer preparer = dataSource -> {};
        DatabaseTemplate template = new DatabaseTemplate("template");

        when(mockProvider.createTemplate(any())).thenReturn(template);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenThrow(new ProviderException("test exception"));

        assertThatCode(() -> optimizingProvider.createDatabase(preparer))
                .isExactlyInstanceOf(ProviderException.class)
                .hasMessage("test exception");
    }
}