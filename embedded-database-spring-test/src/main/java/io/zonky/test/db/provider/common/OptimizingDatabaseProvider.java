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

package io.zonky.test.db.provider.common;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.zonky.test.db.flyway.preparer.BaselineFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.CleanFlywayDatabasePreparer;
import io.zonky.test.db.flyway.preparer.FlywayDatabasePreparer;
import io.zonky.test.db.preparer.CompositeDatabasePreparer;
import io.zonky.test.db.preparer.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseProvider;
import io.zonky.test.db.provider.EmbeddedDatabase;
import io.zonky.test.db.provider.ProviderException;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OptimizingDatabaseProvider implements DatabaseProvider {

    private final DatabaseProvider provider;

    public OptimizingDatabaseProvider(DatabaseProvider provider) {
        this.provider = provider;
    }

    @Override
    public EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException {
//        if (preparer instanceof CompositeDatabasePreparer) {
//            List<DatabasePreparer> preparers = ((CompositeDatabasePreparer) preparer).getPreparers();
//
//            long flywayPreparersCount = preparers.stream()
//                    .filter(this::isFlywayPreparer)
//                    .map(FlywayDatabasePreparer.class::cast)
//                    .map(schemaExtractor()).distinct().count();
//
//            // TODO: consider whether the optimization is always applicable
//            if (flywayPreparersCount == 1) {
//                return provider.createDatabase(new CompositeDatabasePreparer(squashPreparers(preparers)));
//            }
//        }

        return provider.createDatabase(preparer);
    }

    protected Function<FlywayDatabasePreparer, Set<String>> schemaExtractor() {
        return preparer -> ImmutableSet.copyOf(preparer.getFlywayDescriptor().getSchemas());
    }

    protected List<DatabasePreparer> squashPreparers(List<DatabasePreparer> preparers) {
        if (preparers.stream().anyMatch(this::isBaselinePreparer)) {
            return preparers;
        }

        int reverseIndex = Iterables.indexOf(Lists.reverse(preparers), this::isCleanPreparer);
        if (reverseIndex == -1) {
            return preparers;
        }

        Stream<DatabasePreparer> beforeClean = preparers.subList(0, preparers.size() - 1 - reverseIndex).stream().filter(p -> !isFlywayPreparer(p));
        Stream<DatabasePreparer> afterClean = preparers.subList(preparers.size() - 1 - reverseIndex, preparers.size()).stream();

        return Stream.concat(beforeClean, afterClean).collect(Collectors.toList());
    }

    protected boolean isFlywayPreparer(DatabasePreparer preparer) {
        return preparer instanceof FlywayDatabasePreparer;
    }

    protected boolean isBaselinePreparer(DatabasePreparer preparer) {
        return preparer instanceof BaselineFlywayDatabasePreparer;
    }

    protected boolean isCleanPreparer(DatabasePreparer preparer) {
        return preparer instanceof CleanFlywayDatabasePreparer;
    }
}
