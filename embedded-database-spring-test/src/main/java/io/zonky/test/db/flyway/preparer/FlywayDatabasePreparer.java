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
import io.zonky.test.db.flyway.FlywayWrapper;
import io.zonky.test.db.preparer.DatabasePreparer;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.Objects;

public abstract class FlywayDatabasePreparer implements DatabasePreparer {

    private final FlywayDescriptor descriptor;

    public FlywayDatabasePreparer(FlywayDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public FlywayDescriptor getFlywayDescriptor() {
        return descriptor;
    }

    protected abstract void doOperation(Flyway flyway);

    @Override
    public void prepare(DataSource ds) {
        FlywayWrapper wrapper = FlywayWrapper.newInstance();

        descriptor.applyTo(wrapper);
        wrapper.setDataSource(ds);

        doOperation(wrapper.getFlyway());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlywayDatabasePreparer that = (FlywayDatabasePreparer) o;
        return Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor);
    }
}
