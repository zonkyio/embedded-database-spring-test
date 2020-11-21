package io.zonky.test.support;

import io.zonky.test.db.flyway.FlywayClassUtils;
import org.springframework.util.ClassUtils;

import static org.junit.Assume.assumeTrue;

public class TestAssumptions {

    private TestAssumptions() {}

    public static void assumeFlywaySupportsBaselineOperation() {
        assumeFlywayVersion(40);
    }

    public static void assumeFlywaySupportsRepeatableMigrations() {
        assumeFlywayVersion(40);
    }

    public static void assumeFlywaySupportsRepeatableAnnotations() {
        assumeFlywayVersion(42);
    }

    private static void assumeFlywayVersion(int minVersion) {
        assumeTrue(FlywayClassUtils.getFlywayVersion() >= minVersion);
    }

    public static void assumeSpringBootSupportsJdbcTestAnnotation() {
        assumeTrue(ClassUtils.isPresent("org.springframework.boot.test.autoconfigure.jdbc.JdbcTest", TestAssumptions.class.getClassLoader()));
    }
}
