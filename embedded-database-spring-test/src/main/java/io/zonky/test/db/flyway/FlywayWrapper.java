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

import com.google.common.collect.ImmutableList;
import org.aopalliance.intercept.MethodInterceptor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.util.scanner.Scanner;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static io.zonky.test.db.util.ReflectionUtils.getField;
import static io.zonky.test.db.util.ReflectionUtils.invokeConstructor;
import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;
import static io.zonky.test.db.util.ReflectionUtils.invokeStaticMethod;
import static org.springframework.test.util.AopTestUtils.getUltimateTargetObject;

public class FlywayWrapper {

    private static final ClassLoader classLoader = FlywayWrapper.class.getClassLoader();

    private static final int flywayVersion = FlywayClassUtils.getFlywayVersion();

    private final Flyway flyway;
    private final Object config;

    public static FlywayWrapper newInstance() {
        if (flywayVersion >= 60) {
            Object config = invokeStaticMethod(Flyway.class, "configure");
            return new FlywayWrapper(invokeMethod(config, "load"));
        } else {
            return new FlywayWrapper(new Flyway());
        }
    }

    public static FlywayWrapper forBean(Flyway flyway) {
        return new FlywayWrapper(flyway);
    }

    private FlywayWrapper(Flyway flyway) {
        this.flyway = flyway;

        if (flywayVersion >= 51) {
            config = getField(getUltimateTargetObject(flyway), "configuration");
        } else {
            config = getUltimateTargetObject(flyway);
        }
    }

    public Flyway getFlyway() {
        return flyway;
    }

    public Object getConfig() {
        return config;
    }

    public Object clean() {
        return invokeMethod(flyway, "clean");
    }

    public Object baseline() {
        return invokeMethod(flyway, "baseline");
    }

    public Object migrate() {
        return invokeMethod(flyway, "migrate");
    }

    public Collection<ResolvedMigration> getMigrations() {
        try {
            Flyway flyway = getUltimateTargetObject(this.flyway);
            MigrationResolver resolver = createMigrationResolver(flyway);

            if (flywayVersion >= 52) {
                Class<?> contextType = ClassUtils.forName("org.flywaydb.core.api.resolver.Context", classLoader);
                Object contextInstance = ProxyFactory.getProxy(contextType, (MethodInterceptor) invocation ->
                        "getConfiguration".equals(invocation.getMethod().getName()) ? config : invocation.proceed());
                return invokeMethod(resolver, "resolveMigrations", contextInstance);
            } else {
                return resolver.resolveMigrations();
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + e.getMessage());
        }
    }

    private MigrationResolver createMigrationResolver(Flyway flyway) throws ClassNotFoundException {
        if (flywayVersion >= 63) {
            Object scanner = createScanner(flyway);
            Object sqlScriptFactory = createMock("org.flywaydb.core.internal.sqlscript.SqlScriptFactory");
            Object sqlScriptExecutorFactory = createMock("org.flywaydb.core.internal.sqlscript.SqlScriptExecutorFactory");
            Object parsingContext = invokeConstructor("org.flywaydb.core.internal.parser.ParsingContext");
            return invokeMethod(flyway, "createMigrationResolver", scanner, scanner, sqlScriptExecutorFactory, sqlScriptFactory, parsingContext);
        } else if (flywayVersion >= 60) {
            Object scanner = createScanner(flyway);
            Object sqlScriptFactory = createMock("org.flywaydb.core.internal.sqlscript.SqlScriptFactory");
            Object sqlScriptExecutorFactory = createMock("org.flywaydb.core.internal.sqlscript.SqlScriptExecutorFactory");
            return invokeMethod(flyway, "createMigrationResolver", scanner, scanner, sqlScriptExecutorFactory, sqlScriptFactory);
        } else if (flywayVersion >= 52) {
            Object scanner = createScanner(flyway);
            Object placeholderReplacer = createMock("org.flywaydb.core.internal.placeholder.PlaceholderReplacer");
            Object factory = invokeConstructor("org.flywaydb.core.internal.database.postgresql.PostgreSQLSqlStatementBuilderFactory", placeholderReplacer);
            return invokeMethod(flyway, "createMigrationResolver", null, scanner, scanner, factory);
        } else if (flywayVersion >= 51) {
            Object scanner = createScanner(flyway);
            Object placeholderReplacer = invokeMethod(flyway, "createPlaceholderReplacer");
            return invokeMethod(flyway, "createMigrationResolver", null, scanner, placeholderReplacer);
        } else if (flywayVersion >= 40) {
            Scanner scanner = new Scanner(flyway.getClassLoader());
            return invokeMethod(flyway, "createMigrationResolver", null, scanner);
        } else {
            return invokeMethod(flyway, "createMigrationResolver", (Object) null);
        }
    }

    private Object createScanner(Flyway flyway) throws ClassNotFoundException {
        if (flywayVersion >= 70) {
            return invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                    ClassUtils.forName("org.flywaydb.core.api.migration.JavaMigration", classLoader),
                    Arrays.asList((Object[]) invokeMethod(config, "getLocations")),
                    invokeMethod(config, "getClassLoader"),
                    invokeMethod(config, "getEncoding"),
                    false,
                    getField(flyway, "resourceNameCache"),
                    getField(flyway, "locationScannerCache"));
        }
        if (flywayVersion >= 63) {
            try {
                // this code is only for version 6.3.3 and above
                return invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                        ClassUtils.forName("org.flywaydb.core.api.migration.JavaMigration", classLoader),
                        Arrays.asList((Object[]) invokeMethod(config, "getLocations")),
                        invokeMethod(config, "getClassLoader"),
                        invokeMethod(config, "getEncoding"),
                        getField(flyway, "resourceNameCache"),
                        getField(flyway, "locationScannerCache"));
            } catch (RuntimeException ex) {
                if (flywayVersion > 63) {
                    throw ex;
                }
                // try next branch
            }
        }
        if (flywayVersion >= 61) {
            return invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                    ClassUtils.forName("org.flywaydb.core.api.migration.JavaMigration", classLoader),
                    Arrays.asList((Object[]) invokeMethod(config, "getLocations")),
                    invokeMethod(config, "getClassLoader"),
                    invokeMethod(config, "getEncoding"),
                    getField(flyway, "resourceNameCache"));
        }
        if (flywayVersion >= 60) {
            try {
                // this code is only for version 6.0.7 and above
                return invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                        ClassUtils.forName("org.flywaydb.core.api.migration.JavaMigration", classLoader),
                        Arrays.asList((Object[]) invokeMethod(config, "getLocations")),
                        invokeMethod(config, "getClassLoader"),
                        invokeMethod(config, "getEncoding"));
            } catch (RuntimeException ex) {
                // try next branch
            }
        }
        if (flywayVersion >= 52) {
            return invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                    Arrays.asList((Object[]) invokeMethod(config, "getLocations")),
                    invokeMethod(config, "getClassLoader"),
                    invokeMethod(config, "getEncoding"));
        }
        if (flywayVersion >= 51) {
            return invokeConstructor(Scanner.class, config);
        }

        throw new IllegalStateException("Unsupported flyway version: " + flywayVersion);
    }

    public DataSource getDataSource() {
        return getValue(config, "getDataSource");
    }

    public void setDataSource(DataSource dataSource) {
        setValue(config, "setDataSource", dataSource);
    }

    public List<String> getLocations() {
        if (flywayVersion >= 51) {
            return ImmutableList.copyOf(Arrays.stream(getArray(config, "getLocations"))
                    .map(location -> (String) invokeMethod(location, "getDescriptor"))
                    .iterator());
        } else {
            return ImmutableList.copyOf(getArray(config, "getLocations"));
        }
    }

    public void setLocations(List<String> locations) {
        if (flywayVersion >= 51) {
            invokeMethod(config, "setLocationsAsStrings", (Object) locations.toArray(new String[0]));
        } else {
            invokeMethod(config, "setLocations", (Object) locations.toArray(new String[0]));
        }
    }

    public List<String> getSchemas() {
        return ImmutableList.copyOf(getArray(config, "getSchemas"));
    }

    public void setSchemas(List<String> schemas) {
        setValue(config, "setSchemas", schemas.toArray(new String[0]));
    }

    public String getTable() {
        return getValue(config, "getTable");
    }

    public void setTable(String table) {
        setValue(config, "setTable", table);
    }

    public String getSqlMigrationPrefix() {
        return getValue(config, "getSqlMigrationPrefix");
    }

    public void setSqlMigrationPrefix(String sqlMigrationPrefix) {
        setValue(config, "setSqlMigrationPrefix", sqlMigrationPrefix);
    }

    public String getRepeatableSqlMigrationPrefix() {
        if (flywayVersion >= 40) {
            return getValue(config, "getRepeatableSqlMigrationPrefix");
        } else {
            return "R";
        }
    }

    public void setRepeatableSqlMigrationPrefix(String repeatableSqlMigrationPrefix) {
        if (flywayVersion >= 40) {
            setValue(config, "setRepeatableSqlMigrationPrefix", repeatableSqlMigrationPrefix);
        } else if (!Objects.equals(repeatableSqlMigrationPrefix, getRepeatableSqlMigrationPrefix())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public String getSqlMigrationSeparator() {
        return getValue(config, "getSqlMigrationSeparator");
    }

    public void setSqlMigrationSeparator(String sqlMigrationSeparator) {
        setValue(config, "setSqlMigrationSeparator", sqlMigrationSeparator);
    }

    public List<String> getSqlMigrationSuffixes() {
        if (flywayVersion >= 50) {
            return ImmutableList.copyOf(getArray(config, "getSqlMigrationSuffixes"));
        } else {
            return ImmutableList.of(getValue(config, "getSqlMigrationSuffix"));
        }
    }

    public void setSqlMigrationSuffixes(List<String> sqlMigrationSuffixes) {
        if (flywayVersion >= 50) {
            setValue(config, "setSqlMigrationSuffixes", sqlMigrationSuffixes.toArray(new String[0]));
        } else if (sqlMigrationSuffixes.size() == 1) {
            setValue(config, "setSqlMigrationSuffix", sqlMigrationSuffixes.get(0));
        } else {
            throw new IllegalArgumentException("Only a single element is supported for the current flyway version");
        }
    }

    public boolean isIgnoreMissingMigrations() {
        if (flywayVersion >= 41) {
            return getValue(config, "isIgnoreMissingMigrations");
        } else {
            return false;
        }
    }

    public void setIgnoreMissingMigrations(boolean ignoreMissingMigrations) {
        if (flywayVersion >= 41) {
            setValue(config, "setIgnoreMissingMigrations", ignoreMissingMigrations);
        } else if (!Objects.equals(ignoreMissingMigrations, isIgnoreMissingMigrations())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isIgnoreFutureMigrations() {
        if (flywayVersion >= 40) {
            return getValue(config, "isIgnoreFutureMigrations");
        } else {
            return true;
        }
    }

    public void setIgnoreFutureMigrations(boolean ignoreFutureMigrations) {
        if (flywayVersion >= 40) {
            setValue(config, "setIgnoreFutureMigrations", ignoreFutureMigrations);
        } else if (!Objects.equals(ignoreFutureMigrations, isIgnoreFutureMigrations())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isValidateOnMigrate() {
        return getValue(config, "isValidateOnMigrate");
    }

    public void setValidateOnMigrate(boolean validateOnMigrate) {
        setValue(config, "setValidateOnMigrate", validateOnMigrate);
    }

    private static <T> T getValue(Object target, String method) {
        return invokeMethod(target, method);
    }

    private static <E> E[] getArray(Object target, String method) {
        return invokeMethod(target, method);
    }

    private static void setValue(Object target, String method, Object value) {
        invokeMethod(target, method, value);
    }

    private static Object createMock(String className) throws ClassNotFoundException {
        Class<?> proxyInterface = ClassUtils.forName(className, classLoader);
        return ProxyFactory.getProxy(proxyInterface, (MethodInterceptor) invocation -> null);
    }
}
