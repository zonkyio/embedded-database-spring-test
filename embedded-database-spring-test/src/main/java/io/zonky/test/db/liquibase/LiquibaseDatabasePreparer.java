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

package io.zonky.test.db.liquibase;

import io.zonky.test.db.preparer.DatabasePreparer;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;

import javax.sql.DataSource;
import java.util.Objects;

public class LiquibaseDatabasePreparer implements DatabasePreparer {

    private final LiquibaseDescriptor descriptor;

    public LiquibaseDatabasePreparer(LiquibaseDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public void prepare(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();

        descriptor.applyTo(liquibase);
        liquibase.setDataSource(dataSource);

        try {
            liquibase.afterPropertiesSet();
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Unexpected error when running Liquibase", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiquibaseDatabasePreparer that = (LiquibaseDatabasePreparer) o;
        return Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor);
    }
}
