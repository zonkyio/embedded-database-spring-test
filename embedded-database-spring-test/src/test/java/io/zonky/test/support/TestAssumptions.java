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

    public static void assumeYandexSupportsCurrentPostgresVersion() {
        if (isLinux()) {
            String postgresVersion = System.getenv("ZONKY_TEST_DATABASE_POSTGRES_YANDEX-PROVIDER_POSTGRES-VERSION");
            assumeTrue(postgresVersion != null);
            String majorVersion = postgresVersion.substring(0, postgresVersion.indexOf("."));
            assumeTrue(Integer.parseInt(majorVersion) < 11);
        }
    }

    private static boolean isLinux() {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            return false;
        }
        return osName.startsWith("Linux") || osName.startsWith("LINUX");
    }
}
