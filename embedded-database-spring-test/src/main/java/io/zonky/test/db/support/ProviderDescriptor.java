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

package io.zonky.test.db.support;

import com.google.common.base.MoreObjects;
import org.springframework.util.Assert;

import java.util.Objects;

public final class ProviderDescriptor {

    private final String providerName;
    private final String databaseName;

    public static ProviderDescriptor of(String providerName, String databaseName) {
        return new ProviderDescriptor(providerName, databaseName);
    }

    private ProviderDescriptor(String providerName, String databaseName) {
        Assert.notNull(databaseName, "Database name must not be null");
        Assert.notNull(providerName, "Provider name must not be null");
        this.databaseName = databaseName.toLowerCase();
        this.providerName = providerName.toLowerCase();
    }

    public String getProviderName() {
        return providerName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProviderDescriptor that = (ProviderDescriptor) o;
        return Objects.equals(providerName, that.providerName) &&
                Objects.equals(databaseName, that.databaseName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerName, databaseName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("providerName", providerName)
                .add("databaseName", databaseName)
                .toString();
    }
}
