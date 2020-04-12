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

package io.zonky.test.db.context;

import com.google.common.base.MoreObjects;
import org.springframework.util.Assert;

import java.util.Objects;

public final class DatabaseDescriptor {

    private final String databaseName;
    private final String providerName;

    public static DatabaseDescriptor of(String databaseName, String providerName) {
        return new DatabaseDescriptor(databaseName, providerName);
    }

    private DatabaseDescriptor(String databaseName, String providerName) {
        Assert.notNull(databaseName, "Database name must not be null");
        Assert.notNull(providerName, "Provider name must not be null");
        this.databaseName = databaseName.toLowerCase();
        this.providerName = providerName.toLowerCase();
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getProviderName() {
        return providerName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseDescriptor that = (DatabaseDescriptor) o;
        return Objects.equals(databaseName, that.databaseName) &&
                Objects.equals(providerName, that.providerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseName, providerName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("databaseName", databaseName)
                .add("providerName", providerName)
                .toString();
    }
}
