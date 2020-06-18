/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.flyway;

import org.apache.commons.io.IOUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import static io.zonky.test.db.util.ReflectionUtils.getField;
import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;
import static io.zonky.test.db.util.ReflectionUtils.invokeStaticMethod;
import static java.nio.charset.StandardCharsets.UTF_8;

public class FlywayClassUtils {

    private static final int flywayVersion = loadFlywayVersion();
    private static final boolean flywayPro = loadFlywayPro();

    private FlywayClassUtils() {}

    private static int loadFlywayVersion() {
        try {
            ClassPathResource versionResource = new ClassPathResource("org/flywaydb/core/internal/version.txt", FlywayClassUtils.class.getClassLoader());
            if (versionResource.exists()) {
                return Integer.parseInt(IOUtils.readLines(versionResource.getInputStream(), UTF_8).get(0).replaceAll("^(\\d+)\\.(\\d+).*", "$1$2"));
            } else if (ClassUtils.hasMethod(Flyway.class, "isPlaceholderReplacement")) {
                return 32;
            } else if (ClassUtils.hasMethod(Flyway.class, "getBaselineVersion")) {
                return 31;
            } else {
                return 30;
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(FlywayClassUtils.class).error("Unexpected error when resolving flyway version", e);
            return 0;
        }
    }

    private static boolean loadFlywayPro() {
        if (flywayVersion < 50) {
            return false;
        }
        try {
            if (flywayVersion >= 60) {
                Object flywayConfig = invokeStaticMethod(Flyway.class, "configure");
                invokeMethod(flywayConfig, "getUndoSqlMigrationPrefix");
            } else if (flywayVersion >= 51) {
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
