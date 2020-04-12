package io.zonky.test.util;

import io.zonky.test.db.flyway.FlywayClassUtils;
import org.flywaydb.core.Flyway;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.util.List;

import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;
import static io.zonky.test.db.util.ReflectionUtils.invokeStaticMethod;
import static java.util.Collections.emptyList;

public class FlywayTestUtils {

    private FlywayTestUtils() {}

    public static Flyway createFlyway(DataSource dataSource, String schema) {
        return createFlyway(dataSource, schema, emptyList());
    }

    public static Flyway createFlyway(DataSource dataSource, String schema, List<String> locations) {
        return createFlyway(dataSource, schema, locations, true);
    }

    public static Flyway createFlyway(DataSource dataSource, String schema, List<String> locations, boolean validateOnMigrate) {
        int flywayVersion = FlywayClassUtils.getFlywayVersion();

        if (flywayVersion >= 60) {
            Object config = invokeStaticMethod(Flyway.class, "configure");
            invokeMethod(config, "dataSource", dataSource);
            invokeMethod(config, "schemas", (Object) new String[]{ schema });
            invokeMethod(config, "validateOnMigrate", validateOnMigrate);
            if (!CollectionUtils.isEmpty(locations)) {
                invokeMethod(config, "locations", (Object) locations.toArray(new String[0]));
            }
            return (Flyway) invokeMethod(config, "load");
        } else {
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setSchemas(schema);
            flyway.setValidateOnMigrate(validateOnMigrate);
            if (!CollectionUtils.isEmpty(locations)) {
                flyway.setLocations(locations.toArray(new String[0]));
            }
            return flyway;
        }
    }
}
