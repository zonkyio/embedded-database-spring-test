package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.MigrationResolver;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.zonky.test.db.util.ReflectionUtils.getField;
import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;

/**
 * Represents an <b>immutable</b> snapshot of Flyway's configuration.
 * It is required because the mutability of flyway instances.
 */
public class FlywayConfigSnapshot {

    private static final int flywayVersion = FlywayClassUtils.getFlywayVersion();
    private static final boolean isFlywayPro = FlywayClassUtils.isFlywayPro();

    // not included in equals and hashCode methods
    private final ClassLoader classLoader;
    private final DataSource dataSource;
    private final Object[] callbacks; // the callbacks are modified during the migration

    // included in equals and hashCode methods
    // but it will work only for empty arrays or null values (that is common use-case)
    // because of missing equals and hashCode methods
    // on classes implementing these interfaces
    private final List<MigrationResolver> resolvers;
    private final List<Object> errorHandlers;
    private final Object resourceProvider;
    private final Object javaMigrationClassProvider;

    // included in equals and hashCode methods
    private final MigrationVersion baselineVersion;
    private final MigrationVersion target;
    private final List<Object> locations;
    private final List<String> schemas;
    private final List<String> sqlMigrationSuffixes;
    private final List<Class<?>> javaMigrations;
    private final List<String> errorOverrides;
    private final Map<String, String> placeholders;
    private final Map<String, String> jdbcProperties;
    private final List<Object> cherryPick;
    private final String table;
    private final String tablespace;
    private final String defaultSchemaName;
    private final String baselineDescription;
    private final String undoSqlMigrationPrefix;
    private final String repeatableSqlMigrationPrefix;
    private final String sqlMigrationSeparator;
    private final String sqlMigrationPrefix;
    private final String placeholderPrefix;
    private final String placeholderSuffix;
    private final Object encoding;
    private final String initSql;
    private final String licenseKey;
    private final String url;
    private final String user;
    private final String password;
    private final boolean skipDefaultResolvers;
    private final boolean skipDefaultCallbacks;
    private final boolean skipExecutingMigrations;
    private final boolean placeholderReplacement;
    private final boolean baselineOnMigrate;
    private final boolean outOfOrder;
    private final boolean ignoreMissingMigrations;
    private final boolean ignoreIgnoredMigrations;
    private final boolean ignorePendingMigrations;
    private final boolean ignoreFutureMigrations;
    private final boolean validateMigrationNaming;
    private final boolean validateOnMigrate;
    private final boolean cleanOnValidationError;
    private final boolean cleanDisabled;
    private final boolean allowMixedMigrations;
    private final boolean createSchemas;
    private final boolean mixed;
    private final boolean group;
    private final String installedBy;
    private final boolean dryRun;
    private final boolean stream;
    private final boolean batch;
    private final boolean oracleSqlPlus;
    private final boolean oracleSqlplusWarn;
    private final String oracleKerberosConfigFile;
    private final String oracleKerberosCacheFile;
    private final boolean outputQueryResults;
    private final int connectRetries;
    private final int lockRetryCount;

    public FlywayConfigSnapshot(Flyway flyway) {
        final Object config;
        if (flywayVersion >= 51) {
            config = getField(flyway, "configuration");
        } else {
            config = flyway;
        }

        this.classLoader = getValue(config, "getClassLoader");
        this.dataSource = getValue(config, "getDataSource");
        this.resolvers = ImmutableList.copyOf(getArray(config, "getResolvers"));
        this.callbacks = getValue(config, "getCallbacks");
        this.sqlMigrationSeparator = getValue(config, "getSqlMigrationSeparator");
        this.sqlMigrationPrefix = getValue(config, "getSqlMigrationPrefix");
        this.placeholderSuffix = getValue(config, "getPlaceholderSuffix");
        this.placeholderPrefix = getValue(config, "getPlaceholderPrefix");
        this.placeholders = ImmutableMap.copyOf(getMap(config, "getPlaceholders"));
        this.target = getValue(config, "getTarget");
        this.table = getValue(config, "getTable");
        this.schemas = ImmutableList.copyOf(getArray(config, "getSchemas"));
        this.encoding = getValue(config, "getEncoding");
        this.locations = ImmutableList.copyOf(getArray(config, "getLocations"));
        this.outOfOrder = getValue(config, "isOutOfOrder");
        this.validateOnMigrate = getValue(config, "isValidateOnMigrate");
        this.cleanOnValidationError = getValue(config, "isCleanOnValidationError");

        if (flywayVersion >= 31) {
            this.baselineVersion = getValue(config, "getBaselineVersion");
            this.baselineDescription = getValue(config, "getBaselineDescription");
            this.baselineOnMigrate = getValue(config, "isBaselineOnMigrate");
        } else {
            this.baselineVersion = null;
            this.baselineDescription = null;
            this.baselineOnMigrate = false;
        }

        if (flywayVersion >= 32) {
            this.placeholderReplacement = getValue(config, "isPlaceholderReplacement");
        } else {
            this.placeholderReplacement = true;
        }

        if (flywayVersion >= 40) {
            this.skipDefaultResolvers = getValue(config, "isSkipDefaultResolvers");
            this.skipDefaultCallbacks = getValue(config, "isSkipDefaultCallbacks");
            this.repeatableSqlMigrationPrefix = getValue(config, "getRepeatableSqlMigrationPrefix");
            this.ignoreFutureMigrations = getValue(config, "isIgnoreFutureMigrations");
            this.cleanDisabled = getValue(config, "isCleanDisabled");
        } else {
            this.skipDefaultResolvers = false;
            this.skipDefaultCallbacks = false;
            this.repeatableSqlMigrationPrefix = "R";
            this.ignoreFutureMigrations = true;
            this.cleanDisabled = false;
        }

        if (flywayVersion >= 41) {
            this.ignoreMissingMigrations = getValue(config, "isIgnoreMissingMigrations");
            this.installedBy = getValue(config, "getInstalledBy");
        } else {
            this.ignoreMissingMigrations = false;
            this.installedBy = null;
        }

        if (flywayVersion >= 41 && flywayVersion < 50) {
            this.allowMixedMigrations = getValue(config, "isAllowMixedMigrations");
        } else {
            this.allowMixedMigrations = false;
        }

        if (flywayVersion >= 42) {
            this.mixed = getValue(config, "isMixed");
            this.group = getValue(config, "isGroup");
        } else {
            this.mixed = false;
            this.group = false;
        }

        if (flywayVersion >= 50) {
            this.sqlMigrationSuffixes = ImmutableList.copyOf(getArray(config, "getSqlMigrationSuffixes"));
        } else {
            String sqlMigrationSuffix = getValue(config, "getSqlMigrationSuffix");
            this.sqlMigrationSuffixes = ImmutableList.of(sqlMigrationSuffix);
        }

        if (flywayVersion >= 50 && isFlywayPro) {
            this.undoSqlMigrationPrefix = getValue(config, "getUndoSqlMigrationPrefix");
            this.dryRun = getValue(config, "getDryRunOutput") != null;
        } else {
            this.undoSqlMigrationPrefix = null;
            this.dryRun = false;
        }

        if (flywayVersion >= 50 && flywayVersion < 60 && isFlywayPro) {
            this.errorHandlers = ImmutableList.copyOf(getArray(config, "getErrorHandlers"));
        } else {
            this.errorHandlers = ImmutableList.of();
        }

        if (flywayVersion >= 51) {
            this.ignoreIgnoredMigrations = getValue(config, "isIgnoreIgnoredMigrations");
        } else {
            this.ignoreIgnoredMigrations = false;
        }

        if (flywayVersion >= 51 && isFlywayPro) {
            this.errorOverrides = ImmutableList.copyOf(getArray(config, "getErrorOverrides"));
            this.stream = getValue(config, "isStream");
            this.batch = getValue(config, "isBatch");
            this.oracleSqlPlus = getValue(config, "isOracleSqlplus");
        } else {
            this.errorOverrides = ImmutableList.of();
            this.stream = false;
            this.batch = false;
            this.oracleSqlPlus = false;
        }

        if (flywayVersion >= 52) {
            this.ignorePendingMigrations = getValue(config, "isIgnorePendingMigrations");
            this.connectRetries = getValue(config, "getConnectRetries");
            this.initSql = getValue(config, "getInitSql");
        } else {
            this.ignorePendingMigrations = false;
            this.connectRetries = 0;
            this.initSql = null;
        }

        if (flywayVersion >= 52 && isFlywayPro) {
            this.licenseKey = getValue(config, "getLicenseKey");
        } else {
            this.licenseKey = null;
        }

        if (flywayVersion >= 60) {
            this.tablespace = getValue(config, "getTablespace");
            this.javaMigrations = ImmutableList.copyOf(Arrays.stream(getArray(config, "getJavaMigrations"))
                    .map(Object::getClass)
                    .collect(Collectors.toList()));
        } else {
            this.tablespace = null;
            this.javaMigrations = ImmutableList.of();
        }

        if (flywayVersion >= 60 && isFlywayPro) {
            this.oracleSqlplusWarn = getValue(config, "isOracleSqlplusWarn");
            this.outputQueryResults = getValue(config, "outputQueryResults");
        } else {
            this.oracleSqlplusWarn = false;
            this.outputQueryResults = true;
        }

        if (flywayVersion >= 61) {
            this.defaultSchemaName = getValue(config, "getDefaultSchema");
        } else {
            this.defaultSchemaName = null;
        }

        if (flywayVersion >= 62) {
            this.validateMigrationNaming = getValue(config, "isValidateMigrationNaming");
        } else {
            this.validateMigrationNaming = false;
        }

        if (flywayVersion >= 65) {
            this.resourceProvider = getValue(config, "getResourceProvider");
            this.javaMigrationClassProvider = getValue(config, "getJavaMigrationClassProvider");
            this.createSchemas = getValue(config, "getCreateSchemas");
        } else {
            this.resourceProvider = null;
            this.javaMigrationClassProvider = null;
            this.createSchemas = true;
        }

        if (flywayVersion >= 70) {
            this.url = getValue(config, "getUrl");
            this.user = getValue(config, "getUser");
            this.password = getValue(config, "getPassword");
        } else {
            this.url = null;
            this.user = null;
            this.password = null;
        }

        if (flywayVersion >= 70 && isFlywayPro) {
            this.jdbcProperties = ImmutableMap.copyOf(getMap(config, "getJdbcProperties"));
            this.cherryPick = ImmutableList.copyOf(getArray(config, "getCherryPick"));
            this.skipExecutingMigrations = getValue(config, "isSkipExecutingMigrations");
            this.oracleKerberosConfigFile = getValue(config, "getOracleKerberosConfigFile");
            this.oracleKerberosCacheFile = getValue(config, "getOracleKerberosCacheFile");
        } else {
            this.jdbcProperties = ImmutableMap.of();;
            this.cherryPick = ImmutableList.of();;
            this.skipExecutingMigrations = false;
            this.oracleKerberosConfigFile = "";
            this.oracleKerberosCacheFile = "";
        }

        if (flywayVersion >= 71) {
            this.lockRetryCount = getValue(config, "getLockRetryCount");
        } else {
            this.lockRetryCount = 50;
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

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public MigrationVersion getBaselineVersion() {
        return baselineVersion;
    }

    public String getBaselineDescription() {
        return baselineDescription;
    }

    public List<MigrationResolver> getResolvers() {
        return resolvers;
    }

    public Object getResourceProvider() {
        return resourceProvider;
    }

    public Object getJavaMigrationClassProvider() {
        return javaMigrationClassProvider;
    }

    public boolean isSkipDefaultResolvers() {
        return skipDefaultResolvers;
    }

    public Object[] getCallbacks() {
        return callbacks;
    }

    public boolean isSkipDefaultCallbacks() {
        return skipDefaultCallbacks;
    }

    public boolean isSkipExecutingMigrations() {
        return skipExecutingMigrations;
    }

    public List<String> getSqlMigrationSuffixes() {
        return sqlMigrationSuffixes;
    }

    public List<Class<?>> getJavaMigrations() {
        return javaMigrations;
    }

    public String getUndoSqlMigrationPrefix() {
        return undoSqlMigrationPrefix;
    }

    public String getRepeatableSqlMigrationPrefix() {
        return repeatableSqlMigrationPrefix;
    }

    public String getSqlMigrationSeparator() {
        return sqlMigrationSeparator;
    }

    public String getSqlMigrationPrefix() {
        return sqlMigrationPrefix;
    }

    public boolean isPlaceholderReplacement() {
        return placeholderReplacement;
    }

    public String getPlaceholderSuffix() {
        return placeholderSuffix;
    }

    public String getPlaceholderPrefix() {
        return placeholderPrefix;
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    public Map<String, String> getJdbcProperties() {
        return jdbcProperties;
    }

    public List<Object> getCherryPick() {
        return cherryPick;
    }

    public MigrationVersion getTarget() {
        return target;
    }

    public String getTable() {
        return table;
    }

    public String getTablespace() {
        return tablespace;
    }

    public String getDefaultSchemaName() {
        return defaultSchemaName;
    }

    public List<String> getSchemas() {
        return schemas;
    }

    public Object getEncoding() {
        return encoding;
    }

    public String getInitSql() {
        return initSql;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public List<Object> getLocations() {
        return locations;
    }

    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }

    public boolean isOutOfOrder() {
        return outOfOrder;
    }

    public boolean isIgnoreMissingMigrations() {
        return ignoreMissingMigrations;
    }

    public boolean isIgnoreIgnoredMigrations() {
        return ignoreIgnoredMigrations;
    }

    public boolean isIgnorePendingMigrations() {
        return ignorePendingMigrations;
    }

    public boolean isIgnoreFutureMigrations() {
        return ignoreFutureMigrations;
    }

    public boolean isValidateMigrationNaming() {
        return validateMigrationNaming;
    }

    public boolean isValidateOnMigrate() {
        return validateOnMigrate;
    }

    public boolean isCleanOnValidationError() {
        return cleanOnValidationError;
    }

    public boolean isCleanDisabled() {
        return cleanDisabled;
    }

    public boolean isAllowMixedMigrations() {
        return allowMixedMigrations;
    }

    public boolean isCreateSchemas() {
        return createSchemas;
    }

    public boolean isMixed() {
        return mixed;
    }

    public boolean isGroup() {
        return group;
    }

    public String getInstalledBy() {
        return installedBy;
    }

    public List<Object> getErrorHandlers() {
        return errorHandlers;
    }

    public List<String> getErrorOverrides() {
        return errorOverrides;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isStream() {
        return stream;
    }

    public boolean isBatch() {
        return batch;
    }

    public boolean isOracleSqlPlus() {
        return oracleSqlPlus;
    }

    public boolean isOracleSqlplusWarn() {
        return oracleSqlplusWarn;
    }

    public String getOracleKerberosConfigFile() {
        return oracleKerberosConfigFile;
    }

    public String getOracleKerberosCacheFile() {
        return oracleKerberosCacheFile;
    }

    public boolean isOutputQueryResults() {
        return outputQueryResults;
    }

    public int getConnectRetries() {
        return connectRetries;
    }

    public int getLockRetryCount() {
        return lockRetryCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlywayConfigSnapshot that = (FlywayConfigSnapshot) o;
        return skipDefaultResolvers == that.skipDefaultResolvers &&
                skipDefaultCallbacks == that.skipDefaultCallbacks &&
                skipExecutingMigrations == that.skipExecutingMigrations &&
                placeholderReplacement == that.placeholderReplacement &&
                baselineOnMigrate == that.baselineOnMigrate &&
                outOfOrder == that.outOfOrder &&
                ignoreMissingMigrations == that.ignoreMissingMigrations &&
                ignoreIgnoredMigrations == that.ignoreIgnoredMigrations &&
                ignorePendingMigrations == that.ignorePendingMigrations &&
                ignoreFutureMigrations == that.ignoreFutureMigrations &&
                validateMigrationNaming == that.validateMigrationNaming &&
                validateOnMigrate == that.validateOnMigrate &&
                cleanOnValidationError == that.cleanOnValidationError &&
                cleanDisabled == that.cleanDisabled &&
                allowMixedMigrations == that.allowMixedMigrations &&
                createSchemas == that.createSchemas &&
                mixed == that.mixed &&
                group == that.group &&
                dryRun == that.dryRun &&
                stream == that.stream &&
                batch == that.batch &&
                oracleSqlPlus == that.oracleSqlPlus &&
                oracleSqlplusWarn == that.oracleSqlplusWarn &&
                outputQueryResults == that.outputQueryResults &&
                connectRetries == that.connectRetries &&
                lockRetryCount == that.lockRetryCount &&
                Objects.equals(resolvers, that.resolvers) &&
                Objects.equals(errorHandlers, that.errorHandlers) &&
                Objects.equals(resourceProvider, that.resourceProvider) &&
                Objects.equals(javaMigrationClassProvider, that.javaMigrationClassProvider) &&
                Objects.equals(baselineVersion, that.baselineVersion) &&
                Objects.equals(target, that.target) &&
                Objects.equals(locations, that.locations) &&
                Objects.equals(schemas, that.schemas) &&
                Objects.equals(sqlMigrationSuffixes, that.sqlMigrationSuffixes) &&
                Objects.equals(javaMigrations, that.javaMigrations) &&
                Objects.equals(errorOverrides, that.errorOverrides) &&
                Objects.equals(placeholders, that.placeholders) &&
                Objects.equals(jdbcProperties, that.jdbcProperties) &&
                Objects.equals(cherryPick, that.cherryPick) &&
                Objects.equals(table, that.table) &&
                Objects.equals(tablespace, that.tablespace) &&
                Objects.equals(defaultSchemaName, that.defaultSchemaName) &&
                Objects.equals(baselineDescription, that.baselineDescription) &&
                Objects.equals(undoSqlMigrationPrefix, that.undoSqlMigrationPrefix) &&
                Objects.equals(repeatableSqlMigrationPrefix, that.repeatableSqlMigrationPrefix) &&
                Objects.equals(sqlMigrationSeparator, that.sqlMigrationSeparator) &&
                Objects.equals(sqlMigrationPrefix, that.sqlMigrationPrefix) &&
                Objects.equals(placeholderPrefix, that.placeholderPrefix) &&
                Objects.equals(placeholderSuffix, that.placeholderSuffix) &&
                Objects.equals(encoding, that.encoding) &&
                Objects.equals(initSql, that.initSql) &&
                Objects.equals(licenseKey, that.licenseKey) &&
                Objects.equals(installedBy, that.installedBy) &&
                Objects.equals(url, that.url) &&
                Objects.equals(user, that.user) &&
                Objects.equals(password, that.password) &&
                Objects.equals(oracleKerberosConfigFile, that.oracleKerberosConfigFile) &&
                Objects.equals(oracleKerberosCacheFile, that.oracleKerberosCacheFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                resolvers, errorHandlers, resourceProvider, javaMigrationClassProvider,
                baselineVersion, target, locations, schemas, sqlMigrationSuffixes,
                javaMigrations, errorOverrides, placeholders, jdbcProperties, cherryPick,
                table, tablespace, defaultSchemaName,
                baselineDescription, undoSqlMigrationPrefix, repeatableSqlMigrationPrefix,
                sqlMigrationSeparator, sqlMigrationPrefix, placeholderPrefix,
                placeholderSuffix, encoding, initSql, licenseKey, url, user, password,
                skipDefaultResolvers, skipDefaultCallbacks, skipExecutingMigrations,
                placeholderReplacement, baselineOnMigrate, outOfOrder,
                ignoreMissingMigrations, ignoreIgnoredMigrations, ignorePendingMigrations,
                ignoreFutureMigrations, validateMigrationNaming, validateOnMigrate,
                cleanOnValidationError, cleanDisabled, allowMixedMigrations, createSchemas,
                mixed, group, installedBy, dryRun, stream, batch,
                oracleSqlPlus, oracleSqlplusWarn, oracleKerberosConfigFile, oracleKerberosCacheFile,
                outputQueryResults, connectRetries, lockRetryCount);
    }
}
