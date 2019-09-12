package io.zonky.test.db.config;

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
