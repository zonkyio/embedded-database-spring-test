/*
 * Compile-only stub of a Spring Boot 4 interface.
 * Allows the library to provide implementations for Spring Boot 4
 * while still being compiled against Spring Boot 3.x.
 *
 * Original source: org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails
 * https://github.com/spring-projects/spring-boot
 */

package org.springframework.boot.flyway.autoconfigure;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

public interface FlywayConnectionDetails extends ConnectionDetails {

    String getUsername();

    String getPassword();

    String getJdbcUrl();

    default String getDriverClassName() {
        return null;
    }

}
