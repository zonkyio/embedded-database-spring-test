/*
 * Compile-only stub of a Spring Boot 4 annotation.
 * Allows the library to provide implementations for Spring Boot 4
 * while still being compiled against Spring Boot 3.x.
 *
 * Original source: org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
 * https://github.com/spring-projects/spring-boot
 */

package org.springframework.boot.jdbc.test.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface JdbcTest {

    String[] properties() default {};

}
