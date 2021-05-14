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

package io.zonky.test.db.util;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PropertyUtils {

    private PropertyUtils() {}

    public static Map<String, String> extractAll(Environment environment, String prefix) {
        prefix += ".";
        Map<String, String> properties = new HashMap<>();
        if (environment instanceof ConfigurableEnvironment) {
            for (PropertySource<?> propertySource : ((ConfigurableEnvironment) environment).getPropertySources()) {
                if (propertySource instanceof EnumerablePropertySource) {
                    for (String key : ((EnumerablePropertySource) propertySource).getPropertyNames()) {
                        if (key.startsWith(prefix)) {
                            properties.put(key.substring(prefix.length()), String.valueOf(propertySource.getProperty(key)));
                        }
                    }
                }
            }
        }
        return properties;
    }

    public static <E extends Enum<E>> E getEnumProperty(Environment environment, String key, Class<E> enumType) {
        return getEnumProperty(environment, key, enumType, null);
    }

    public static <E extends Enum<E>> E getEnumProperty(Environment environment, String key, Class<E> enumType, E defaultValue) {
        String enumName = environment.getProperty(key, String.class);
        if (enumName == null) {
            return defaultValue;
        }
        String normalizedEnumName = enumName.trim().replaceAll("-", "_").toUpperCase(Locale.ENGLISH);
        for (E candidate : EnumSet.allOf(enumType)) {
            if (candidate.name().equals(normalizedEnumName)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No enum constant " + enumType.getCanonicalName() + "." + enumName);
    }
}
