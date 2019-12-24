package io.zonky.test.db.flyway;

import org.apache.commons.io.IOUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.invokeMethod;

public class FlywayClassUtils {

    private static final int flywayVersion = loadFlywayVersion();
    private static final boolean flywayPro = loadFlywayPro();

    private FlywayClassUtils() {}

    private static int loadFlywayVersion() {
        try {
            ClassPathResource versionResource = new ClassPathResource("org/flywaydb/core/internal/version.txt", FlywayClassUtils.class.getClassLoader());
            if (versionResource.exists()) {
                return Integer.valueOf(IOUtils.readLines(versionResource.getInputStream(), UTF_8).get(0).replaceAll("^(\\d+)\\.(\\d+).*", "$1$2"));
            } else if (ClassUtils.hasMethod(Flyway.class, "isPlaceholderReplacement")) {
                return 32;
            } else if (ClassUtils.hasMethod(Flyway.class, "getBaselineVersion")) {
                return 31;
            } else {
                return 30;
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(FlywayClassUtils.class).error("Unexpected error occurred while resolving flyway version", e);
            return 0;
        }
    }

    private static boolean loadFlywayPro() {
        if (flywayVersion < 50) {
            return false;
        }
        try {
            if (flywayVersion >= 51) {
                Object flywayConfig = getField(new Flyway(), "configuration");
                invokeMethod(flywayConfig, "getUndoSqlMigrationPrefix");
            } else {
                new Flyway().getUndoSqlMigrationPrefix();
            }
            return true;
        } catch (FlywayException e) {
            return false;
        }
    }

    public static int getFlywayVersion() {
        return flywayVersion;
    }

    public static boolean isFlywayPro() {
        return flywayPro;
    }
}
