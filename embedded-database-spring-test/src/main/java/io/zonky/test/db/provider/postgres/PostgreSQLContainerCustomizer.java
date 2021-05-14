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

package io.zonky.test.db.provider.postgres;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Callback interface that can be implemented by beans wishing to customize
 * the postgres container before it is used by a {@code DockerPostgresDatabaseProvider}.
 */
@FunctionalInterface
public interface PostgreSQLContainerCustomizer {

    /**
     * Customize the given {@link PostgreSQLContainer}.
     * @param container the container to customize
     */
    void customize(PostgreSQLContainer container);

}
