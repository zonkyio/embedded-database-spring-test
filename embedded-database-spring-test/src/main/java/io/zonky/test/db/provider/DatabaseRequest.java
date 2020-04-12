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

import java.util.Objects;

public class DatabaseRequest {

    private final DatabasePreparer preparer;
    private final DatabaseTemplate template;

    public static DatabaseRequest of(DatabasePreparer preparer) {
        return new DatabaseRequest(preparer, null);
    }

    public static DatabaseRequest of(DatabasePreparer preparer, DatabaseTemplate template) {
        return new DatabaseRequest(preparer, template);
    }

    private DatabaseRequest(DatabasePreparer preparer, DatabaseTemplate template) {
        this.template = template;
        this.preparer = preparer;
    }

    public DatabasePreparer getPreparer() {
        return preparer;
    }

    public DatabaseTemplate getTemplate() {
        return template;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseRequest that = (DatabaseRequest) o;
        return Objects.equals(preparer, that.preparer) &&
                Objects.equals(template, that.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preparer, template);
    }
}
