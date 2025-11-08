/*
 * Copyright 2025 the original author or authors.
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

package io.zonky.test.db.init;

import com.google.common.base.MoreObjects;
import io.zonky.test.db.preparer.DatabasePreparer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class ScriptDatabasePreparer implements DatabasePreparer {

    private final List<String> scriptLocations;
    private final boolean continueOnError;
    private final String separator;
    private final Charset encoding;

    public ScriptDatabasePreparer(List<String> scriptLocations) {
        this.scriptLocations = scriptLocations;
        this.continueOnError = false;
        this.separator = ";";
        this.encoding = null;
    }

    public ScriptDatabasePreparer(List<String> scriptLocations, boolean continueOnError, String separator, Charset encoding) {
        this.scriptLocations = scriptLocations;
        this.continueOnError = continueOnError;
        this.separator = separator;
        this.encoding = encoding;
    }

    @Override
    public long estimatedDuration() {
        return 10;
    }

    @Override
    public void prepare(DataSource dataSource) throws SQLException {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(continueOnError);
        populator.setSeparator(separator);
        if (encoding != null) {
            populator.setSqlScriptEncoding(encoding.name());
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String scriptLocation : scriptLocations) {
            Resource resource = resolver.getResource(scriptLocation);
            populator.addScript(resource);
        }
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptDatabasePreparer that = (ScriptDatabasePreparer) o;
        return continueOnError == that.continueOnError
                && Objects.equals(scriptLocations, that.scriptLocations)
                && Objects.equals(separator, that.separator)
                && Objects.equals(encoding, that.encoding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scriptLocations, continueOnError, separator, encoding);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scriptLocation", scriptLocations)
                .add("continueOnError", continueOnError)
                .add("separator", separator)
                .add("encoding", encoding)
                .toString();
    }
}
