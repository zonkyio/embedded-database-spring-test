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
import com.google.common.collect.ImmutableMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.util.scanner.Scanner;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.AopTestUtils;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.zonky.test.db.util.ReflectionUtils.getField;
import static io.zonky.test.db.util.ReflectionUtils.invokeConstructor;
import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;
import static io.zonky.test.db.util.ReflectionUtils.invokeStaticMethod;
import static org.mockito.Mockito.mock;

public class FlywayWrapper {

    private static final ClassLoader classLoader = FlywayWrapper.class.getClassLoader();

    private static final int flywayVersion = FlywayClassUtils.getFlywayVersion();
    private static final boolean isFlywayPro = FlywayClassUtils.isFlywayPro();

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
        this.flyway = AopTestUtils.getUltimateTargetObject(flyway);

        if (flywayVersion >= 51) {
            config = getField(this.flyway, "configuration");
        } else {
            config = this.flyway;
        }
    }

    public Flyway getFlyway() {
        return flyway;
    }

    public Collection<ResolvedMigration> getMigrations() {
        try {
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
        if (flywayVersion >= 60) {
            // TODO: replace using mockito mocks
            Object sqlScriptFactory = mock(ClassUtils.forName("org.flywaydb.core.internal.sqlscript.SqlScriptFactory", classLoader));
            Object sqlScriptExecutorFactory = mock(ClassUtils.forName("org.flywaydb.core.internal.sqlscript.SqlScriptExecutorFactory", classLoader));
            Object scanner;

            try {
                scanner = invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                        ClassUtils.forName("org.flywaydb.core.api.migration.JavaMigration", classLoader),
                        Arrays.asList((Object[]) invokeMethod(config, "getLocations")),
                        invokeMethod(config, "getClassLoader"),
                        invokeMethod(config, "getEncoding"));
            } catch (RuntimeException ex) {
                scanner = invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                        Arrays.asList((Object[]) invokeMethod(config, "getLocations")),
                        invokeMethod(config, "getClassLoader"),
                        invokeMethod(config, "getEncoding"));
            }

            return invokeMethod(flyway, "createMigrationResolver", scanner, scanner, sqlScriptExecutorFactory, sqlScriptFactory);
        } else if (flywayVersion >= 52) {
            // TODO: replace using mockito mocks
            Object database = null;
            Object placeholderReplacer = mock(ClassUtils.forName("org.flywaydb.core.internal.placeholder.PlaceholderReplacer", classLoader));
            Object factory = invokeConstructor("org.flywaydb.core.internal.database.postgresql.PostgreSQLSqlStatementBuilderFactory", placeholderReplacer);

            Object scanner = invokeConstructor("org.flywaydb.core.internal.scanner.Scanner",
                    Arrays.asList((Object[]) invokeMethod(config, "getLocations")),
                    invokeMethod(config, "getClassLoader"),
                    invokeMethod(config, "getEncoding"));
            return invokeMethod(flyway, "createMigrationResolver", database, scanner, scanner, factory);
        } else if (flywayVersion >= 51) {
            Object scanner = invokeConstructor(Scanner.class, config);
            Object placeholderReplacer = invokeMethod(flyway, "createPlaceholderReplacer");
            return invokeMethod(flyway, "createMigrationResolver", null, scanner, placeholderReplacer);
        } else if (flywayVersion >= 40) {
            Scanner scanner = new Scanner(flyway.getClassLoader());
            return invokeMethod(flyway, "createMigrationResolver", null, scanner);
        } else {
            return invokeMethod(flyway, "createMigrationResolver", (Object) null);
        }
    }

    public List<String> getLocations() {
        if (flywayVersion >= 51) {
            return Arrays.stream(getArray(config, "getLocations"))
                    .map(location -> (String) invokeMethod(location, "getDescriptor"))
                    .collect(Collectors.toList());
        } else {
            return ImmutableList.copyOf(flyway.getLocations());
        }
    }

    public void setLocations(List<String> locations) {
        if (flywayVersion >= 51) {
            invokeMethod(config, "setLocationsAsStrings", (Object) locations.toArray(new String[0]));
        } else {
            flyway.setLocations(locations.toArray(new String[0]));
        }
    }

    public DataSource getDataSource() {
        return getValue(config, "getDataSource");
    }

    public void setDataSource(DataSource dataSource) {
        setValue(config, "setDataSource", dataSource);
    }

    public MigrationVersion getBaselineVersion() {
        if (flywayVersion >= 31) {
            return getValue(config, "getBaselineVersion");
        } else {
            return null;
        }
    }

    public void setBaselineVersion(MigrationVersion baselineVersion) {
        if (flywayVersion >= 31) {
            setValue(config, "setBaselineVersion", baselineVersion);
        } else if (!Objects.equals(baselineVersion, getBaselineVersion())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public String getBaselineDescription() {
        if (flywayVersion >= 31) {
            return getValue(config, "getBaselineDescription");
        } else {
            return null;
        }
    }

    public void setBaselineDescription(String baselineDescription) {
        if (flywayVersion >= 31) {
            setValue(config, "setBaselineDescription", baselineDescription);
        } else if (!Objects.equals(baselineDescription, getBaselineDescription())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public List<MigrationResolver> getResolvers() {
        return ImmutableList.copyOf(getArray(config, "getResolvers"));
    }

    public void setResolvers(List<MigrationResolver> resolvers) {
        setValue(config, "setResolvers", resolvers.toArray(new MigrationResolver[0]));
    }

    public boolean isSkipDefaultResolvers() {
        if (flywayVersion >= 40) {
            return getValue(config, "isSkipDefaultResolvers");
        } else {
            return false;
        }
    }

    public void setSkipDefaultResolvers(boolean skipDefaultResolvers) {
        if (flywayVersion >= 40) {
            setValue(config, "setSkipDefaultResolvers", skipDefaultResolvers);
        } else if (!Objects.equals(skipDefaultResolvers, isSkipDefaultResolvers())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public List<Object> getCallbacks() {
        return ImmutableList.copyOf(getArray(config, "getCallbacks"));
    }

    public void setCallbacks(List<Object> callbacks) {
        try {
            if (flywayVersion >= 51) {
                Class<?> callbackType = ClassUtils.forName("org.flywaydb.core.api.callback.Callback", classLoader);
                setValue(config, "setCallbacks", callbacks.toArray(((Object[]) Array.newInstance(callbackType, 0))));
            } else {
                Class<?> callbackType = ClassUtils.forName("org.flywaydb.core.api.callback.FlywayCallback", classLoader);
                setValue(config, "setCallbacks", callbacks.toArray(((Object[]) Array.newInstance(callbackType, 0))));
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + e.getMessage());
        }
    }

    public boolean isSkipDefaultCallbacks() {
        if (flywayVersion >= 40) {
            return getValue(config, "isSkipDefaultCallbacks");
        } else {
            return false;
        }
    }

    public void setSkipDefaultCallbacks(boolean skipDefaultCallbacks) {
        if (flywayVersion >= 40) {
            setValue(config, "setSkipDefaultCallbacks", skipDefaultCallbacks);
        } else if (!Objects.equals(skipDefaultCallbacks, isSkipDefaultCallbacks())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
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
        } else if (!Objects.equals(sqlMigrationSuffixes, getSqlMigrationSuffixes())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public List<Object> getJavaMigrations() {
        if (flywayVersion >= 60) {
            return ImmutableList.copyOf(getArray(config, "getJavaMigrations"));
        } else {
            return ImmutableList.of();
        }
    }

    public void setJavaMigrations(List<Object> javaMigrations) {
        try {
            if (flywayVersion >= 60) {
                Class<?> migrationType = ClassUtils.forName("org.flywaydb.core.api.migration.JavaMigration", classLoader);
                setValue(config, "setJavaMigrations", javaMigrations.toArray(((Object[]) Array.newInstance(migrationType, 0))));
            } else if (!Objects.equals(javaMigrations, getJavaMigrations())) {
                throw new UnsupportedOperationException("This method is not supported in current Flyway version");
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + e.getMessage());
        }
    }

    public String getUndoSqlMigrationPrefix() {
        if (flywayVersion >= 50 && isFlywayPro) {
            return getValue(config, "getUndoSqlMigrationPrefix");
        } else {
            return null;
        }
    }

    public void setUndoSqlMigrationPrefix(String undoSqlMigrationPrefix) {
        if (flywayVersion >= 50 && isFlywayPro) {
            setValue(config, "setUndoSqlMigrationPrefix", undoSqlMigrationPrefix);
        } else if (!Objects.equals(undoSqlMigrationPrefix, getUndoSqlMigrationPrefix())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
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

    public String getSqlMigrationPrefix() {
        return getValue(config, "getSqlMigrationPrefix");
    }

    public void setSqlMigrationPrefix(String sqlMigrationPrefix) {
        setValue(config, "setSqlMigrationPrefix", sqlMigrationPrefix);
    }

    public boolean isPlaceholderReplacement() {
        if (flywayVersion >= 32) {
            return getValue(config, "isPlaceholderReplacement");
        } else {
            return true;
        }
    }

    public void setPlaceholderReplacement(boolean placeholderReplacement) {
        if (flywayVersion >= 32) {
            setValue(config, "setPlaceholderReplacement", placeholderReplacement);
        } else if (!Objects.equals(placeholderReplacement, isPlaceholderReplacement())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public String getPlaceholderSuffix() {
        return getValue(config, "getPlaceholderSuffix");
    }

    public void setPlaceholderSuffix(String placeholderSuffix) {
        setValue(config, "setPlaceholderSuffix", placeholderSuffix);
    }

    public String getPlaceholderPrefix() {
        return getValue(config, "getPlaceholderPrefix");
    }

    public void setPlaceholderPrefix(String placeholderPrefix) {
        setValue(config, "setPlaceholderPrefix", placeholderPrefix);
    }

    public Map<String, String> getPlaceholders() {
        return ImmutableMap.copyOf(getMap(config, "getPlaceholders"));
    }

    public void setPlaceholders(Map<String, String> placeholders) {
        setValue(config, "setPlaceholders", placeholders);
    }

    public MigrationVersion getTargetVersion() {
        return getValue(config, "getTarget");
    }

    public void setTarget(MigrationVersion targetVersion) {
        setValue(config, "setTarget", targetVersion);
    }

    public String getTable() {
        return getValue(config, "getTable");
    }

    public void setTable(String table) {
        setValue(config, "setTable", table);
    }

    public String getTablespace() {
        if (flywayVersion >= 60) {
            return getValue(config, "getTablespace");
        } else {
            return null;
        }
    }

    public void setTablespace(String tablespace) {
        if (flywayVersion >= 60) {
            setValue(config, "setTablespace", tablespace);
        } else if (!Objects.equals(tablespace, getTablespace())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public List<String> getSchemas() {
        return ImmutableList.copyOf(getArray(config, "getSchemas"));
    }

    public void setSchemas(List<String> schemas) {
        setValue(config, "setSchemas", schemas.toArray(new String[0]));
    }

    public String getEncoding() {
        if (flywayVersion >= 51) {
            return ((Charset) getValue(config, "getEncoding")).name();
        } else {
            return getValue(config, "getEncoding");
        }
    }

    public void setEncoding(String encoding) {
        if (flywayVersion >= 51) {
            setValue(config, "setEncoding", Charset.forName(encoding));
        } else {
            setValue(config, "setEncoding", encoding);
        }
    }

    public String getInitSql() {
        if (flywayVersion >= 52) {
            return getValue(config, "getInitSql");
        } else {
            return null;
        }
    }

    public void setInitSql(String initSql) {
        if (flywayVersion >= 52) {
            setValue(config, "setInitSql", initSql);
        } else if (!Objects.equals(initSql, getInitSql())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public String getLicenseKey() {
        if (flywayVersion >= 52 && isFlywayPro) {
            return getValue(config, "getLicenseKey");
        } else {
            return null;
        }
    }

    public void setLicenseKey(String licenseKey) {
        if (flywayVersion >= 52 && isFlywayPro) {
            setValue(config, "setLicenseKey", licenseKey);
        } else if (!Objects.equals(licenseKey, getLicenseKey())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isBaselineOnMigrate() {
        if (flywayVersion >= 31) {
            return getValue(config, "isBaselineOnMigrate");
        } else {
            return false;
        }
    }

    public void setBaselineOnMigrate(boolean baselineOnMigrate) {
        if (flywayVersion >= 31) {
            setValue(config, "setBaselineOnMigrate", baselineOnMigrate);
        } else if (!Objects.equals(baselineOnMigrate, isBaselineOnMigrate())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isOutOfOrder() {
        return getValue(config, "isOutOfOrder");
    }

    public void setOutOfOrder(boolean outOfOrder) {
        setValue(config, "setOutOfOrder", outOfOrder);
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

    public boolean isIgnoreIgnoredMigrations() {
        if (flywayVersion >= 51) {
            return getValue(config, "isIgnoreIgnoredMigrations");
        } else {
            return false;
        }
    }

    public void setIgnoreIgnoredMigrations(boolean ignoreIgnoredMigrations) {
        if (flywayVersion >= 51) {
            setValue(config, "setIgnoreIgnoredMigrations", ignoreIgnoredMigrations);
        } else if (!Objects.equals(ignoreIgnoredMigrations, isIgnoreIgnoredMigrations())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isIgnorePendingMigrations() {
        if (flywayVersion >= 52) {
            return getValue(config, "isIgnorePendingMigrations");
        } else {
            return false;
        }
    }

    public void setIgnorePendingMigrations(boolean ignorePendingMigrations) {
        if (flywayVersion >= 52) {
            setValue(config, "setIgnorePendingMigrations", ignorePendingMigrations);
        } else if (!Objects.equals(ignorePendingMigrations, isIgnorePendingMigrations())) {
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

    public boolean isCleanOnValidationError() {
        return getValue(config, "isCleanOnValidationError");
    }

    public void setCleanOnValidationError(boolean cleanOnValidationError) {
        setValue(config, "setCleanOnValidationError", cleanOnValidationError);
    }

    public boolean isCleanDisabled() {
        if (flywayVersion >= 40) {
            return getValue(config, "isCleanDisabled");
        } else {
            return false;
        }
    }

    public void setCleanDisabled(boolean cleanDisabled) {
        if (flywayVersion >= 40) {
            setValue(config, "setCleanDisabled", cleanDisabled);
        } else if (!Objects.equals(cleanDisabled, isCleanDisabled())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isAllowMixedMigrations() {
        if (flywayVersion >= 41 && flywayVersion < 50) {
            return getValue(config, "isAllowMixedMigrations");
        } else {
            return false;
        }
    }

    public void setAllowMixedMigrations(boolean allowMixedMigrations) {
        if (flywayVersion >= 41 && flywayVersion < 50) {
            setValue(config, "setAllowMixedMigrations", allowMixedMigrations);
        } else if (!Objects.equals(allowMixedMigrations, isAllowMixedMigrations())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isMixed() {
        if (flywayVersion >= 42) {
            return getValue(config, "isMixed");
        } else {
            return false;
        }
    }

    public void setMixed(boolean mixed) {
        if (flywayVersion >= 42) {
            setValue(config, "setMixed", mixed);
        } else if (!Objects.equals(mixed, isMixed())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isGroup() {
        if (flywayVersion >= 42) {
            return getValue(config, "isGroup");
        } else {
            return false;
        }
    }

    public void setGroup(boolean group) {
        if (flywayVersion >= 42) {
            setValue(config, "setGroup", group);
        } else if (!Objects.equals(group, isGroup())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public String getInstalledBy() {
        if (flywayVersion >= 41) {
            return getValue(config, "getInstalledBy");
        } else {
            return null;
        }
    }

    public void setInstalledBy(String installedBy) {
        if (flywayVersion >= 41) {
            setValue(config, "setInstalledBy", installedBy);
        } else if (!Objects.equals(installedBy, getInstalledBy())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public List<Object> getErrorHandlers() {
        if (flywayVersion >= 50 && flywayVersion < 60 && isFlywayPro) {
            return ImmutableList.copyOf(getArray(config, "getErrorHandlers"));
        } else {
            return ImmutableList.of();
        }
    }

    public void setErrorHandlers(List<Object> errorHandlers) {
        try {
            if (flywayVersion >= 50 && flywayVersion < 60 && isFlywayPro) {
                Class<?> handlerType = ClassUtils.forName("org.flywaydb.core.api.errorhandler.ErrorHandler", classLoader);
                setValue(config, "setErrorHandlers", errorHandlers.toArray(((Object[]) Array.newInstance(handlerType, 0))));
            } else if (!Objects.equals(errorHandlers, getErrorHandlers())) {
                throw new UnsupportedOperationException("This method is not supported in current Flyway version");
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + e.getMessage());
        }
    }

    public List<String> getErrorOverrides() {
        if (flywayVersion >= 51 && isFlywayPro) {
            return ImmutableList.copyOf(getArray(config, "getErrorOverrides"));
        } else {
            return ImmutableList.of();
        }
    }

    public void setErrorOverrides(List<String> errorOverrides) {
        if (flywayVersion >= 51 && isFlywayPro) {
            setValue(config, "setErrorOverrides", errorOverrides.toArray(new String[0]));
        } else if (!Objects.equals(errorOverrides, getErrorOverrides())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public OutputStream getDryRunOutput() {
        if (flywayVersion >= 50 && isFlywayPro) {
            return getValue(config, "getDryRunOutput");
        } else {
            return null;
        }
    }

    public void setDryRunOutput(OutputStream dryRunOutput) {
        if (flywayVersion >= 50 && isFlywayPro) {
            setValue(config, "setDryRunOutput", dryRunOutput);
        } else if (!Objects.equals(dryRunOutput, getDryRunOutput())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }

    }

    public boolean isStream() {
        if (flywayVersion >= 51 && isFlywayPro) {
            return getValue(config, "isStream");
        } else {
            return false;
        }
    }

    public void setStream(boolean stream) {
        if (flywayVersion >= 51 && isFlywayPro) {
            setValue(config, "setStream", stream);
        } else if (!Objects.equals(stream, isStream())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isBatch() {
        if (flywayVersion >= 51 && isFlywayPro) {
            return getValue(config, "isBatch");
        } else {
            return false;
        }
    }

    public void setBatch(boolean batch) {
        if (flywayVersion >= 51 && isFlywayPro) {
            setValue(config, "setBatch", batch);
        } else if (!Objects.equals(batch, isBatch())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isOracleSqlPlus() {
        if (flywayVersion >= 51 && isFlywayPro) {
            return getValue(config, "isOracleSqlplus");
        } else {
            return false;
        }
    }

    public void setOracleSqlPlus(boolean oracleSqlPlus) {
        if (flywayVersion >= 51 && isFlywayPro) {
            setValue(config, "setOracleSqlplus", oracleSqlPlus);
        } else if (!Objects.equals(oracleSqlPlus, isOracleSqlPlus())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public boolean isOracleSqlplusWarn() {
        if (flywayVersion >= 60 && isFlywayPro) {
            return getValue(config, "isOracleSqlplusWarn");
        } else {
            return false;
        }
    }

    public void setOracleSqlplusWarn(boolean oracleSqlplusWarn) {
        if (flywayVersion >= 60 && isFlywayPro) {
            setValue(config, "setOracleSqlplusWarn", oracleSqlplusWarn);
        } else if (!Objects.equals(oracleSqlplusWarn, isOracleSqlplusWarn())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    public int getConnectRetries() {
        if (flywayVersion >= 52) {
            return getValue(config, "getConnectRetries");
        } else {
            return 0;
        }
    }

    public void setConnectRetries(int connectRetries) {
        if (flywayVersion >= 52) {
            setValue(config, "setConnectRetries", connectRetries);
        } else if (!Objects.equals(connectRetries, getConnectRetries())) {
            throw new UnsupportedOperationException("This method is not supported in current Flyway version");
        }
    }

    private static <T> T getValue(Object target, String method) {
        return invokeMethod(target, method);
    }

    private static <E> E[] getArray(Object target, String method) {
        return invokeMethod(target, method);
    }

    private static <K, V> Map<K, V> getMap(Object target, String method) {
        return invokeMethod(target, method);
    }

    private static void setValue(Object target, String method, Object value) {
        invokeMethod(target, method, value);
    }
}
