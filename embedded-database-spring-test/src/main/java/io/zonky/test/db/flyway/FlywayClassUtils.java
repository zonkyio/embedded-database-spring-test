package io.zonky.test.db.flyway;

import org.apache.commons.io.IOUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.test.annotation.FlywayTest;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

public class FlywayClassUtils {

    private static final boolean flywayNameAttributePresent = ClassUtils.hasMethod(FlywayTest.class, "flywayName");
    private static final boolean flywayBaselineAttributePresent = ClassUtils.hasMethod(FlywayTest.class, "invokeBaselineDB");
    private static final boolean repeatableAnnotationPresent = ClassUtils.isPresent(
            "org.flywaydb.test.annotation.FlywayTests", FlywayClassUtils.class.getClassLoader());

    private static final int flywayVersion;
    private static final boolean isFlywayPro;

    static {
        String version;
        try {
            ClassPathResource versionResource = new ClassPathResource("org/flywaydb/core/internal/version.txt", FlywayConfigSnapshot.class.getClassLoader());
            if (versionResource.exists()) {
                version = IOUtils.readLines(versionResource.getInputStream()).get(0).replaceAll("^(\\d+)\\.(\\d+).*", "$1$2");
            } else if (ClassUtils.hasMethod(Flyway.class, "isPlaceholderReplacement")) {
                version = "32";
            } else if (ClassUtils.hasMethod(Flyway.class, "getBaselineVersion")) {
                version = "31";
            } else {
                version = "30";
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(FlywayConfigSnapshot.class).error("Error resolving flyway version", e);
            version = "0";
        }
        flywayVersion = Integer.valueOf(version);

        if (flywayVersion >= 50) {
            boolean isCommercial;
            try {
                new Flyway().getUndoSqlMigrationPrefix();
                isCommercial = true;
            } catch (FlywayException e) {
                isCommercial = false;
            }
            isFlywayPro = isCommercial;
        } else {
            isFlywayPro = false;
        }
    }

    private FlywayClassUtils() {}

    public static boolean isFlywayNameAttributePresent() {
        return flywayNameAttributePresent;
    }

    public static boolean isFlywayBaselineAttributePresent() {
        return flywayBaselineAttributePresent;
    }

    public static boolean isRepeatableFlywayTestAnnotationPresent() {
        return repeatableAnnotationPresent;
    }

    public static int getFlywayVersion() {
        return flywayVersion;
    }

    public static boolean isFlywayPro() {
        return isFlywayPro;
    }
}
