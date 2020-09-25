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

package io.zonky.test.db.preparer;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class CompositeDatabasePreparer implements DatabasePreparer {

    private final List<DatabasePreparer> preparers;

    public CompositeDatabasePreparer(List<DatabasePreparer> preparers) {
        this.preparers = ImmutableList.copyOf(preparers);
    }

    public List<DatabasePreparer> getPreparers() {
        return preparers;
    }

    @Override
    public void prepare(DataSource dataSource) throws SQLException {
        for (DatabasePreparer preparer : preparers) {
            preparer.prepare(dataSource);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeDatabasePreparer that = (CompositeDatabasePreparer) o;
        return Objects.equals(preparers, that.preparers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preparers);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("preparers", preparers)
                .toString();
    }
}
