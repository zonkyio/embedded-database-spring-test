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

package io.zonky.test.db.flyway.preparer;

import com.google.common.base.MoreObjects;
import io.zonky.test.db.flyway.FlywayDescriptor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.util.Location;
import org.flywaydb.core.internal.util.scanner.Scanner;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.Arrays;

public class MigrateFlywayDatabasePreparer extends FlywayDatabasePreparer {

    private final SettableListenableFuture<Integer> result = new SettableListenableFuture<>();
    private final long estimatedDuration;

    public MigrateFlywayDatabasePreparer(FlywayDescriptor descriptor) {
        super(descriptor);

        // TODO: try to optimize it
        Scanner scanner = new Scanner(MigrateFlywayDatabasePreparer.class.getClassLoader());
        long resources = descriptor.getLocations().stream()
                .flatMap(location -> Arrays.stream(scanner.scanForResources(new Location(location), "", new String[] {".sql", ".class"})))
                .count();
        this.estimatedDuration = 50 * resources;
    }

    public MigrateFlywayDatabasePreparer(FlywayDescriptor descriptor, long estimatedDuration) {
        super(descriptor);
        this.estimatedDuration = estimatedDuration;
    }

    public ListenableFuture<Integer> getResult() {
        return result;
    }

    @Override
    public long estimatedDuration() {
        return estimatedDuration;
    }

    @Override
    protected void doOperation(Flyway flyway) {
        try {
            result.set(flyway.migrate());
        } catch (RuntimeException e) {
            result.setException(e);
            throw e;
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("schemas", descriptor.getSchemas())
                .add("locations", descriptor.getLocations())
                .toString();
    }
}
