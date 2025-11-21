package io.zonky.test.support;

import io.zonky.test.db.flyway.FlywayClassUtils;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.ClassUtils;

import java.util.function.Supplier;

import static org.junit.Assume.assumeTrue;

public class TestAssumptions {

    private TestAssumptions() {}

    public static void assumeFlywaySupportsBaselineOperation() {
        assumeFlywayVersion("4");
    }

    public static void assumeFlywaySupportsRepeatableMigrations() {
        assumeFlywayVersion("4");
    }

    public static void assumeFlywaySupportsRepeatableAnnotations() {
        assumeFlywayVersion("4.2");
    }

    private static void assumeFlywayVersion(String minVersion) {
        assumeTrue(FlywayClassUtils.getFlywayVersion().isGreaterThanOrEqualTo(minVersion));
    }

    public static void assumeSpringSupportsInstanceSupplier() {
        assumeTrue(ClassUtils.hasMethod(AbstractBeanDefinition.class, "setInstanceSupplier", Supplier.class));
    }

    public static void assumeSpringSupportsStreamJdbcQueries() {
        assumeTrue(ClassUtils.hasMethod(JdbcTemplate.class, "queryForStream", String.class, RowMapper.class));
    }

    public static void assumeSpringBootSupportsJdbcTestAnnotation() {
        assumeTrue(ClassUtils.isPresent("org.springframework.boot.test.autoconfigure.jdbc.JdbcTest", TestAssumptions.class.getClassLoader()));
    }

    public static void assumeSpringBoot4SupportsJdbcTestAnnotation() {
        assumeTrue(ClassUtils.isPresent("org.springframework.boot.jdbc.test.autoconfigure.JdbcTest", TestAssumptions.class.getClassLoader()));
    }

    public static void assumeLicenceAcceptance() {
        assumeTrue(TestAssumptions.class.getResource("/container-license-acceptance.txt") != null);
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
