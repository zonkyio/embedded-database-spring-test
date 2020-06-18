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

package io.zonky.test.db.provider.mssql;

import io.zonky.test.db.provider.DatabaseTemplate;
import io.zonky.test.db.provider.ProviderException;
import io.zonky.test.db.provider.mssql.MsSQLEmbeddedDatabase.CloseCallback;

import java.sql.SQLException;

public class MsSQLDatabaseTemplate implements DatabaseTemplate {

    private final String templateName;
    private final CloseCallback closeCallback;

    public MsSQLDatabaseTemplate(String templateName, CloseCallback closeCallback) {
        this.templateName = templateName;
        this.closeCallback = closeCallback;
    }

    @Override
    public String getTemplateName() {
        return templateName;
    }

    @Override
    public void close() {
        try {
            closeCallback.call();
        } catch (SQLException e) {
            throw new ProviderException("Unexpected error when releasing the database template", e);
        }
    }
}
