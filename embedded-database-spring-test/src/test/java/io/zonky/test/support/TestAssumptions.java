package io.zonky.test.support;

import io.zonky.test.db.flyway.FlywayClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
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

    public static void assumeYandexSupportsCurrentPostgresVersion() {
        if (SystemUtils.IS_OS_LINUX) {
            String postgresVersion = System.getenv("ZONKY_TEST_DATABASE_POSTGRES_YANDEX-PROVIDER_POSTGRES-VERSION");
            assumeTrue(postgresVersion != null);
            int majorVersion = Integer.parseInt(StringUtils.substringBefore(postgresVersion, "."));
            assumeTrue(majorVersion < 11);
        }
    }
}
