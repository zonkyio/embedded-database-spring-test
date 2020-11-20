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

package io.zonky.test.db.provider;

import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.common.PrefetchingDatabaseProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.zonky.test.support.MockitoAssertions.mockWithName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PrefetchingDatabaseProviderTest {

    @Mock
    private TemplatableDatabaseProvider databaseProvider;

    private PrefetchingDatabaseProvider prefetchingProvider;

    @Before
    public void setUp() {
        prefetchingProvider = new PrefetchingDatabaseProvider(databaseProvider, new MockEnvironment());
    }

    @Test
    public void testPrefetching() {
        DatabasePreparer preparer = mock(DatabasePreparer.class);
        List<EmbeddedDatabase> dataSources = Stream.generate(() -> mock(EmbeddedDatabase.class))
                .limit(5).collect(Collectors.toList());

        BlockingQueue<EmbeddedDatabase> providerReturns = new LinkedBlockingQueue<>(dataSources);
        doAnswer(i -> providerReturns.poll()).when(databaseProvider).createDatabase(same(preparer));

        Set<EmbeddedDatabase> results = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            results.add(prefetchingProvider.createDatabase(preparer));
        }
        assertThat(results).hasSize(3).isSubsetOf(dataSources);

        verify(databaseProvider, timeout(100).times(5)).createDatabase(same(preparer));
    }

    @Test
    public void testDifferentPreparers() {
        DatabasePreparer preparer1 = mock(DatabasePreparer.class);
        DatabasePreparer preparer2 = mock(DatabasePreparer.class);
        DatabasePreparer preparer3 = mock(DatabasePreparer.class);

        doAnswer(i -> mock(EmbeddedDatabase.class, "mockDataSource1")).when(databaseProvider).createDatabase(same(preparer1));
        doAnswer(i -> mock(EmbeddedDatabase.class, "mockDataSource2")).when(databaseProvider).createDatabase(same(preparer2));
        doAnswer(i -> mock(EmbeddedDatabase.class, "mockDataSource3")).when(databaseProvider).createDatabase(same(preparer3));

        assertThat(prefetchingProvider.createDatabase(preparer1)).is(mockWithName("mockDataSource1"));
        assertThat(prefetchingProvider.createDatabase(preparer2)).is(mockWithName("mockDataSource2"));
        assertThat(prefetchingProvider.createDatabase(preparer2)).is(mockWithName("mockDataSource2"));
        assertThat(prefetchingProvider.createDatabase(preparer3)).is(mockWithName("mockDataSource3"));
        assertThat(prefetchingProvider.createDatabase(preparer3)).is(mockWithName("mockDataSource3"));
        assertThat(prefetchingProvider.createDatabase(preparer3)).is(mockWithName("mockDataSource3"));

        verify(databaseProvider, timeout(100).times(9)).createDatabase(any(DatabasePreparer.class));
    }
}