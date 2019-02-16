/*
 * Copyright 2019 the original author or authors.
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

package io.zonky.test.db.provider.impl;

import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.DatabaseType;
import io.zonky.test.db.provider.ProviderType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.zonky.test.assertj.MockitoAssertions.mockWithName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PrefetchingDatabaseProviderTest {

    @Mock
    private ObjectProvider<List<DatabaseProvider>> databaseProviders;

    private DatabaseProvider databaseProvider1;
    private DatabaseProvider databaseProvider2;
    private DatabaseProvider databaseProvider3;

    private PrefetchingDatabaseProvider prefetchingProvider;

    @Before
    public void setUp() {
        databaseProvider1 = spy(new TestDatabaseProvider(DatabaseType.valueOf("database1"), ProviderType.valueOf("provider1")));
        databaseProvider2 = spy(new TestDatabaseProvider(DatabaseType.valueOf("database2"), ProviderType.valueOf("provider1")));
        databaseProvider3 = spy(new TestDatabaseProvider(DatabaseType.valueOf("database2"), ProviderType.valueOf("provider2")));

        when(databaseProviders.getIfAvailable()).thenReturn(ImmutableList.of(databaseProvider1, databaseProvider2, databaseProvider3));

        this.prefetchingProvider = new PrefetchingDatabaseProvider(databaseProviders, new MockEnvironment());
    }

    @Test
    public void testPrefetching() throws Exception {
        DatabasePreparer preparer = mock(DatabasePreparer.class);
        List<DataSource> dataSources = Stream.generate(() -> mock(DataSource.class))
                .limit(6).collect(Collectors.toList());

        BlockingQueue<DataSource> providerReturns = new LinkedBlockingQueue<>(dataSources);
        doAnswer(i -> providerReturns.poll()).when(databaseProvider2).getDatabase(same(preparer));

        Set<DataSource> results = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            results.add(prefetchingProvider.getDatabase(preparer, newDescriptor("database2", "provider1")));
        }
        assertThat(results).hasSize(3).isSubsetOf(dataSources);

        verify(databaseProvider2, timeout(100).times(6)).getDatabase(same(preparer));
        verify(databaseProvider1, never()).getDatabase(any());
        verify(databaseProvider3, never()).getDatabase(any());
    }

    @Test
    public void testMultipleProviders() throws Exception {
        DatabasePreparer preparer = mock(DatabasePreparer.class);

        doAnswer(i -> mock(DataSource.class, "mockDataSource1")).when(databaseProvider1).getDatabase(any());
        doAnswer(i -> mock(DataSource.class, "mockDataSource2")).when(databaseProvider2).getDatabase(any());
        doAnswer(i -> mock(DataSource.class, "mockDataSource3")).when(databaseProvider3).getDatabase(any());

        for (int i = 0; i < 3; i++) {
            assertThat(prefetchingProvider.getDatabase(preparer, newDescriptor("database1", "provider1"))).is(mockWithName("mockDataSource1"));
            assertThat(prefetchingProvider.getDatabase(preparer, newDescriptor("database2", "provider1"))).is(mockWithName("mockDataSource2"));
            assertThat(prefetchingProvider.getDatabase(preparer, newDescriptor("database2", "provider2"))).is(mockWithName("mockDataSource3"));
        }

        verify(databaseProvider1, timeout(100).times(6)).getDatabase(same(preparer));
        verify(databaseProvider2, timeout(100).times(6)).getDatabase(same(preparer));
        verify(databaseProvider3, timeout(100).times(6)).getDatabase(same(preparer));
    }

    @Test
    public void testDifferentPreparers() throws Exception {
        DatabasePreparer preparer1 = mock(DatabasePreparer.class);
        DatabasePreparer preparer2 = mock(DatabasePreparer.class);
        DatabasePreparer preparer3 = mock(DatabasePreparer.class);

        doAnswer(i -> mock(DataSource.class, "mockDataSource1")).when(databaseProvider2).getDatabase(same(preparer1));
        doAnswer(i -> mock(DataSource.class, "mockDataSource2")).when(databaseProvider2).getDatabase(same(preparer2));
        doAnswer(i -> mock(DataSource.class, "mockDataSource3")).when(databaseProvider2).getDatabase(same(preparer3));

        assertThat(prefetchingProvider.getDatabase(preparer1, newDescriptor("database2", "provider1"))).is(mockWithName("mockDataSource1"));
        assertThat(prefetchingProvider.getDatabase(preparer2, newDescriptor("database2", "provider1"))).is(mockWithName("mockDataSource2"));
        assertThat(prefetchingProvider.getDatabase(preparer3, newDescriptor("database2", "provider1"))).is(mockWithName("mockDataSource3"));

        verify(databaseProvider2, timeout(100).times(12)).getDatabase(any());
        verify(databaseProvider1, never()).getDatabase(any());
        verify(databaseProvider3, never()).getDatabase(any());
    }

    private static DatabaseDescriptor newDescriptor(String databaseType, String providerType) {
        return new DatabaseDescriptor(DatabaseType.valueOf(databaseType), ProviderType.valueOf(providerType));
    }

    private static class TestDatabaseProvider implements DatabaseProvider {

        private final DatabaseType databaseType;
        private final ProviderType providerType;

        private TestDatabaseProvider(DatabaseType databaseType, ProviderType providerType) {
            this.databaseType = databaseType;
            this.providerType = providerType;
        }

        @Override
        public DatabaseType getDatabaseType() {
            return databaseType;
        }

        @Override
        public ProviderType getProviderType() {
            return providerType;
        }

        @Override
        public DataSource getDatabase(DatabasePreparer preparer) {
            throw new UnsupportedOperationException("This method must be always mocked!");
        }
    }
}