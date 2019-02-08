/*
 * Copyright 2019 the original author or authors.
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

import org.springframework.util.Assert;

import java.util.Objects;
import java.util.stream.Stream;

public final class ProviderType {

    public static final ProviderType DOCKER = new ProviderType("docker", "org.testcontainers:postgresql");
    public static final ProviderType ZONKY = new ProviderType("zonky", "io.zonky.test:embedded-postgres");
    public static final ProviderType OPENTABLE = new ProviderType("opentable", "com.opentable.components:otj-pg-embedded");
    public static final ProviderType YANDEX = new ProviderType("yandex", "ru.yandex.qatools.embed:postgresql-embedded");

    private final String name;
    private final String dependency;

    public static ProviderType valueOf(String name) {
        Assert.notNull(name, "Provider name must not be null");
        return Stream.of(DOCKER, ZONKY, OPENTABLE, YANDEX)
                .filter(type -> type.name().equals(name.toLowerCase()))
                .findFirst().orElse(new ProviderType(name.toLowerCase()));
    }

    private ProviderType(String name) {
        this(name, null);
    }

    private ProviderType(String name, String dependency) {
        this.name = name;
        this.dependency = dependency;
    }

    public String name() {
        return name;
    }

    String getDependencyInfo() {
        return dependency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProviderType that = (ProviderType) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
