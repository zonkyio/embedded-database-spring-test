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

import io.zonky.test.db.flyway.FlywayDescriptor;
import org.flywaydb.core.Flyway;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

public class MigrateFlywayDatabasePreparer extends FlywayDatabasePreparer {

    private final SettableListenableFuture<Integer> result = new SettableListenableFuture<>();

    public MigrateFlywayDatabasePreparer(FlywayDescriptor descriptor) {
        super(descriptor);
    }

    public ListenableFuture<Integer> getResult() {
        return result;
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
}
