/*
 * Copyright 2016 the original author or authors.
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

import org.apache.commons.lang3.StringUtils;
import org.postgresql.ds.common.BaseDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

public class EmbeddedDatabaseReporter {

    private static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%s/%s?user=%s";

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedDatabaseReporter.class);

    public static void reportDataSource(DataSource dataSource) {
        logger.info("JDBC URL to connect to the embedded database: {}", getJdbcUrl(dataSource));
    }

    // TODO:
    public static void reportDataSource(DataSource dataSource, AnnotatedElement element) {
        logger.info("JDBC URL to connect to the embedded database: {}, scope: {}", getJdbcUrl(dataSource), getElementName(element));
    }

    private static String getJdbcUrl(DataSource dataSource) {
        try {
            BaseDataSource ds = dataSource.unwrap(BaseDataSource.class);
            if (StringUtils.isBlank(ds.getPassword())) {
                return String.format(JDBC_FORMAT, ds.getPortNumber(), ds.getDatabaseName(), ds.getUser());
            } else {
                return String.format(JDBC_FORMAT + "&password=%s", ds.getPortNumber(), ds.getDatabaseName(), ds.getUser(), ds.getPassword());
            }
        } catch (Exception e) {
            logger.warn("Unexpected error occurred while resolving url to the embedded database", e);
            return "unknown";
        }
    }

    private static String getElementName(AnnotatedElement element) {
        if (element instanceof Class) {
            return ((Class) element).getSimpleName();
        } else if (element instanceof Method) {
            Method method = (Method) element;
            return getElementName(method.getDeclaringClass()) + "#" + method.getName();
        } else {
            return element.toString();
        }
    }
}
