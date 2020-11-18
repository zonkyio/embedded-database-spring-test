package io.zonky.test.db.provider;

import com.google.common.collect.ImmutableList;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.common.TemplatingDatabaseProvider;
import io.zonky.test.db.support.TestDatabasePreparer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Objects;

import static io.zonky.test.db.provider.common.TemplatingDatabaseProvider.EMPTY_PREPARER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TemplatingDatabaseProviderTest {

    @Mock
    private TemplatableDatabaseProvider mockProvider;

    private TemplatingDatabaseProvider optimizingProvider;

    @Before
    public void setUp() {
        optimizingProvider = new TemplatingDatabaseProvider(mockProvider, TemplatingDatabaseProvider.Config.builder().build());
    }

    @Test
    public void createNewTemplate() {
        DatabasePreparer preparer = TestDatabasePreparer.empty();
        DatabaseTemplate template = new TestDatabaseTemplate("template");
        EmbeddedDatabase database = mock(EmbeddedDatabase.class);

        when(mockProvider.createTemplate(any())).thenReturn(template);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenReturn(database);

        assertThat(optimizingProvider.createDatabase(preparer)).isSameAs(database);

        InOrder inOrder = inOrder(mockProvider);
        inOrder.verify(mockProvider).createTemplate(databaseRequest(new CompositeDatabasePreparer(ImmutableList.of(preparer))));
        inOrder.verify(mockProvider).createDatabase(databaseRequest(EMPTY_PREPARER, template));
    }

    @Test
    public void reuseExistingTemplate() {
        DatabasePreparer preparer = TestDatabasePreparer.empty();
        DatabaseTemplate template = new TestDatabaseTemplate("template");

        EmbeddedDatabase database1 = mock(EmbeddedDatabase.class);
        EmbeddedDatabase database2 = mock(EmbeddedDatabase.class);
        EmbeddedDatabase database3 = mock(EmbeddedDatabase.class);

        when(mockProvider.createTemplate(any())).thenReturn(template);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenReturn(database1, database2, database3);

        assertThat(optimizingProvider.createDatabase(preparer)).isSameAs(database1);
        assertThat(optimizingProvider.createDatabase(preparer)).isSameAs(database2);
        assertThat(optimizingProvider.createDatabase(preparer)).isSameAs(database3);

        InOrder inOrder = inOrder(mockProvider);
        inOrder.verify(mockProvider).createTemplate(databaseRequest(new CompositeDatabasePreparer(ImmutableList.of(preparer))));
        inOrder.verify(mockProvider, times(3)).createDatabase(databaseRequest(EMPTY_PREPARER, template));
    }

    @Test
    public void alwaysCreateNewTemplate() {
        DatabaseTemplate template1 = new TestDatabaseTemplate("template1");
        DatabaseTemplate template2 = new TestDatabaseTemplate("template2");

        DatabasePreparer preparer1 = TestDatabasePreparer.empty();
        DatabasePreparer preparer2 = TestDatabasePreparer.empty();
        DatabasePreparer preparer3 = TestDatabasePreparer.empty();

        EmbeddedDatabase database1 = mock(EmbeddedDatabase.class);
        EmbeddedDatabase database2 = mock(EmbeddedDatabase.class);

        when(mockProvider.createTemplate(any())).thenReturn(template1).thenReturn(template2);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenReturn(database1, database2);

        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)))).isSameAs(database1);
        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3)))).isSameAs(database2);

        InOrder inOrder = inOrder(mockProvider);
        inOrder.verify(mockProvider).createTemplate(databaseRequest(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2))));
        inOrder.verify(mockProvider).createDatabase(databaseRequest(EMPTY_PREPARER, template1));
        inOrder.verify(mockProvider).createTemplate(databaseRequest(new CompositeDatabasePreparer(ImmutableList.of(preparer3)), template1));
        inOrder.verify(mockProvider).createDatabase(databaseRequest(EMPTY_PREPARER, template2));
    }

    @Test
    public void createNewSpecificTemplate() {
        DatabaseTemplate template1 = new TestDatabaseTemplate("template1");
        DatabaseTemplate template2 = new TestDatabaseTemplate("template2");

        DatabasePreparer preparer1 = TestDatabasePreparer.empty();
        DatabasePreparer preparer2 = TestDatabasePreparer.empty();
        DatabasePreparer preparer3 = TestDatabasePreparer.empty();

        EmbeddedDatabase database1 = mock(EmbeddedDatabase.class);
        EmbeddedDatabase database2 = mock(EmbeddedDatabase.class);

        when(mockProvider.createTemplate(any())).thenReturn(template1, template2);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenReturn(database1, database2);

        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2)))).isSameAs(database1);
        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3)))).isSameAs(database2);
        assertThat(optimizingProvider.createDatabase(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2, preparer3)))).isSameAs(database2);

        InOrder inOrder = inOrder(mockProvider);
        inOrder.verify(mockProvider).createTemplate(databaseRequest(new CompositeDatabasePreparer(ImmutableList.of(preparer1, preparer2))));
        inOrder.verify(mockProvider).createDatabase(databaseRequest(EMPTY_PREPARER, template1));
        inOrder.verify(mockProvider).createTemplate(databaseRequest(new CompositeDatabasePreparer(ImmutableList.of(preparer3)), template1));
        inOrder.verify(mockProvider, times(2)).createDatabase(databaseRequest(EMPTY_PREPARER, template2));
    }

    @Test
    public void creatingTemplateShouldThrowProviderException() {
        DatabasePreparer preparer = TestDatabasePreparer.empty();

        when(mockProvider.createTemplate(any())).thenThrow(new ProviderException("test exception"));

        assertThatCode(() -> optimizingProvider.createDatabase(preparer))
                .isExactlyInstanceOf(ProviderException.class)
                .hasMessage("test exception");
    }
    @Test
    public void creatingDatabaseShouldThrowProviderException() {
        DatabasePreparer preparer = TestDatabasePreparer.empty();
        DatabaseTemplate template = new TestDatabaseTemplate("template");

        when(mockProvider.createTemplate(any())).thenReturn(template);
        when(mockProvider.createDatabase(any(DatabaseRequest.class))).thenThrow(new ProviderException("test exception"));

        assertThatCode(() -> optimizingProvider.createDatabase(preparer))
                .isExactlyInstanceOf(ProviderException.class)
                .hasMessage("test exception");
    }

    private static DatabaseRequest databaseRequest(DatabasePreparer preparer) {
        return databaseRequest(preparer, null);
    }

    private static DatabaseRequest databaseRequest(DatabasePreparer preparer, DatabaseTemplate template) {
        return argThat(new ArgumentMatcher<DatabaseRequest>() {
            @Override
            public boolean matches(Object argument) {
                DatabaseRequest request = (DatabaseRequest) argument;
                return Objects.equals(request.getPreparer(), preparer)
                        && Objects.equals(request.getTemplate(), template);
            }
        });
    }

    private static class TestDatabaseTemplate implements DatabaseTemplate {

        private final String templateName;

        private TestDatabaseTemplate(String templateName) {
            this.templateName = templateName;
        }

        @Override
        public String getTemplateName() {
            return templateName;
        }

        @Override
        public void close() {}
    }
}