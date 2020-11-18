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

import com.google.common.collect.ImmutableMap;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.core.io.ResourceLoader;

import java.util.Map;
import java.util.Objects;

import static io.zonky.test.db.util.ReflectionUtils.getField;

public class LiquibaseDescriptor {

    // not included in equals and hashCode methods
    private final String beanName;
    private final ResourceLoader resourceLoader;

    // included in equals and hashCode methods
    private final String changeLog;
    private final String contexts;
    private final String labels;
    private final String tag;
    private final Map<String, String> parameters;
    private final String defaultSchema;
    private final boolean dropFirst;
    private final boolean shouldRun;

    ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public String getContexts() {
        return contexts;
    }

    public String getLabels() {
        return labels;
    }

    public String getTag() {
        return tag;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public boolean isDropFirst() {
        return dropFirst;
    }

    public boolean isShouldRun() {
        return shouldRun;
    }

    private LiquibaseDescriptor(String beanName, ResourceLoader resourceLoader, String changeLog, String contexts, String labels, String tag, Map<String, String> parameters, String defaultSchema, boolean dropFirst, boolean shouldRun) {
        this.beanName = beanName;
        this.resourceLoader = resourceLoader;
        this.changeLog = changeLog;
        this.contexts = contexts;
        this.labels = labels;
        this.tag = tag;
        this.parameters = parameters != null ? ImmutableMap.copyOf(parameters) : null;
        this.defaultSchema = defaultSchema;
        this.dropFirst = dropFirst;
        this.shouldRun = shouldRun;
    }

    public static LiquibaseDescriptor from(SpringLiquibase liquibase) {
        return new LiquibaseDescriptor(
                liquibase.getBeanName(),
                liquibase.getResourceLoader(),
                liquibase.getChangeLog(),
                liquibase.getContexts(),
                liquibase.getLabels(),
                liquibase.getTag(),
                getField(liquibase, "parameters"),
                liquibase.getDefaultSchema(),
                liquibase.isDropFirst(),
                getField(liquibase, "shouldRun")
        );
    }

    public void applyTo(SpringLiquibase liquibase) {
        liquibase.setBeanName(beanName);
        liquibase.setResourceLoader(resourceLoader);
        liquibase.setChangeLog(changeLog);
        liquibase.setContexts(contexts);
        liquibase.setLabels(labels);
        liquibase.setTag(tag);
        liquibase.setChangeLogParameters(parameters);
        liquibase.setDefaultSchema(defaultSchema);
        liquibase.setDropFirst(dropFirst);
        liquibase.setShouldRun(shouldRun);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiquibaseDescriptor that = (LiquibaseDescriptor) o;
        return dropFirst == that.dropFirst &&
                shouldRun == that.shouldRun &&
                Objects.equals(changeLog, that.changeLog) &&
                Objects.equals(contexts, that.contexts) &&
                Objects.equals(labels, that.labels) &&
                Objects.equals(tag, that.tag) &&
                Objects.equals(parameters, that.parameters) &&
                Objects.equals(defaultSchema, that.defaultSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                changeLog, contexts, labels, tag, parameters,
                defaultSchema, dropFirst, shouldRun);
    }
}
