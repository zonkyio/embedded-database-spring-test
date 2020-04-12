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

package io.zonky.test.db.logging;

import io.zonky.test.db.provider.EmbeddedDatabase;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

public class EmbeddedDatabaseReporter {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedDatabaseReporter.class);

    public static void reportDataSource(String beanName, EmbeddedDatabase database, AnnotatedElement element) {
        String connectionString = database.getUrl() + String.format("?user=%s", database.getUsername());

        if (StringUtils.isNotBlank(database.getPassword())) {
            connectionString += String.format("&password=%s", database.getPassword());
        }

        logger.info("JDBC URL to connect to '{}': url='{}', scope='{}'", beanName, connectionString, getElementName(element));
    }

    private static String getElementName(AnnotatedElement element) {
        if (element instanceof Class<?>) {
            return ((Class<?>) element).getSimpleName();
        } else if (element instanceof Method) {
            Method method = (Method) element;
            return getElementName(method.getDeclaringClass()) + "#" + method.getName();
        } else {
            return element.toString();
        }
    }
}
