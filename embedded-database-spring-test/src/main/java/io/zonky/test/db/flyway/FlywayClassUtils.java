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

import org.flywaydb.core.Flyway;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StreamUtils;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FlywayClassUtils {

    private static final FlywayVersion flywayVersion = loadFlywayVersion();

    private FlywayClassUtils() {}

    private static FlywayVersion loadFlywayVersion() {
        try {
            ClassPathResource versionResource = new ClassPathResource("org/flywaydb/core/internal/version.txt", FlywayClassUtils.class.getClassLoader());
            if (versionResource.exists()) {
                String version = StreamUtils.copyToString(versionResource.getInputStream(), UTF_8);
                return FlywayVersion.parseVersion(version);
            } else if (ClassUtils.hasMethod(Flyway.class, "isPlaceholderReplacement")) {
                return FlywayVersion.parseVersion("3.2");
            } else if (ClassUtils.hasMethod(Flyway.class, "getBaselineVersion")) {
                return FlywayVersion.parseVersion("3.1");
            } else {
                return FlywayVersion.parseVersion("3.0");
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(FlywayClassUtils.class).error("Unexpected error when resolving flyway version", e);
            return FlywayVersion.parseVersion("0");
        }
    }

    public static FlywayVersion getFlywayVersion() {
        return flywayVersion;
    }
}
