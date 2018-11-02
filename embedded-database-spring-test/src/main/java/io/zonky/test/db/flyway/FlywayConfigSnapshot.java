package io.zonky.test.db.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.MigrationResolver;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.invokeMethod;

/**
 * Represents a snapshot of configuration parameters of a flyway instance.
 * It is necessary because of mutability of flyway instances.
 */
public class FlywayConfigSnapshot {

    private static final int flywayVersion = FlywayClassUtils.getFlywayVersion();
    private static final boolean isFlywayPro = FlywayClassUtils.isFlywayPro();

    // not included in equals and hashCode methods
    private final ClassLoader classLoader;
    private final DataSource dataSource;
    private final Object[] callbacks; // the callbacks are modified during the migration

    // included in equals and hashCode methods
    // but it will work only for empty arrays (that is common use-case)
    // because of missing equals and hashCode methods
    // on classes implementing these interfaces,
    // note the properties require special treatment suitable for arrays
    private final MigrationResolver[] resolvers;
    private final Object[] errorHandlers;

    // included in equals and hashCode methods
    // but these properties require special treatment suitable for arrays
    private final Object[] locations;
    private final String[] schemas;
    private final String[] sqlMigrationSuffixes;
    private final String[] errorOverrides;

    // included in equals and hashCode methods
    private final MigrationVersion baselineVersion;
    private final MigrationVersion target;
    private final Map<String, String> placeholders;
    private final String table;
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
    private final boolean skipDefaultResolvers;
    private final boolean skipDefaultCallbacks;
    private final boolean placeholderReplacement;
    private final boolean baselineOnMigrate;
    private final boolean outOfOrder;
    private final boolean ignoreMissingMigrations;
    private final boolean ignoreIgnoredMigrations;
    private final boolean ignorePendingMigrations;
    private final boolean ignoreFutureMigrations;
    private final boolean validateOnMigrate;
    private final boolean cleanOnValidationError;
    private final boolean cleanDisabled;
    private final boolean allowMixedMigrations;
    private final boolean mixed;
    private final boolean group;
    private final String installedBy;
    private final boolean dryRun;
    private final boolean stream;
    private final boolean batch;
    private final boolean oracleSqlPlus;
    private final int connectRetries;

    public FlywayConfigSnapshot(Flyway flyway) {
        final Object config;
        if (flywayVersion >= 51) {
            config = getField(flyway, "configuration");
        } else {
            config = flyway;
        }

        this.classLoader = invokeMethod(config, "getClassLoader");
        this.dataSource = invokeMethod(config, "getDataSource");
        this.resolvers = invokeMethod(config, "getResolvers");
        this.callbacks = invokeMethod(config, "getCallbacks");
        this.sqlMigrationSeparator = invokeMethod(config, "getSqlMigrationSeparator");
        this.sqlMigrationPrefix = invokeMethod(config, "getSqlMigrationPrefix");
        this.placeholderSuffix = invokeMethod(config, "getPlaceholderSuffix");
        this.placeholderPrefix = invokeMethod(config, "getPlaceholderPrefix");
        this.placeholders = invokeMethod(config, "getPlaceholders");
        this.target = invokeMethod(config, "getTarget");
        this.table = invokeMethod(config, "getTable");
        this.schemas = invokeMethod(config, "getSchemas");
        this.encoding = invokeMethod(config, "getEncoding");
        this.locations = invokeMethod(config, "getLocations");
        this.outOfOrder = invokeMethod(config, "isOutOfOrder");
        this.validateOnMigrate = invokeMethod(config, "isValidateOnMigrate");
        this.cleanOnValidationError = invokeMethod(config, "isCleanOnValidationError");

        if (flywayVersion >= 31) {
            this.baselineVersion = invokeMethod(config, "getBaselineVersion");
            this.baselineDescription = invokeMethod(config, "getBaselineDescription");
            this.baselineOnMigrate = invokeMethod(config, "isBaselineOnMigrate");
        } else {
            this.baselineVersion = null;
            this.baselineDescription = null;
            this.baselineOnMigrate = false;
        }

        if (flywayVersion >= 32) {
            this.placeholderReplacement = invokeMethod(config, "isPlaceholderReplacement");
        } else {
            this.placeholderReplacement = true;
        }

        if (flywayVersion >= 40) {
            this.skipDefaultResolvers = invokeMethod(config, "isSkipDefaultResolvers");
            this.skipDefaultCallbacks = invokeMethod(config, "isSkipDefaultCallbacks");
            this.repeatableSqlMigrationPrefix = invokeMethod(config, "getRepeatableSqlMigrationPrefix");
            this.ignoreFutureMigrations = invokeMethod(config, "isIgnoreFutureMigrations");
            this.cleanDisabled = invokeMethod(config, "isCleanDisabled");
        } else {
            this.skipDefaultResolvers = false;
            this.skipDefaultCallbacks = false;
            this.repeatableSqlMigrationPrefix = "R";
            this.ignoreFutureMigrations = true;
            this.cleanDisabled = false;
        }

        if (flywayVersion >= 41) {
            this.ignoreMissingMigrations = invokeMethod(config, "isIgnoreMissingMigrations");
            this.installedBy = invokeMethod(config, "getInstalledBy");
        } else {
            this.ignoreMissingMigrations = false;
            this.installedBy = null;
        }

        if (flywayVersion >= 41 && flywayVersion < 50) {
            this.allowMixedMigrations = invokeMethod(config, "isAllowMixedMigrations");
        } else {
            this.allowMixedMigrations = false;
        }

        if (flywayVersion >= 42) {
            this.mixed = invokeMethod(config, "isMixed");
            this.group = invokeMethod(config, "isGroup");
        } else {
            this.mixed = false;
            this.group = false;
        }

        if (flywayVersion >= 50) {
            this.sqlMigrationSuffixes = invokeMethod(config, "getSqlMigrationSuffixes");
        } else {
            String sqlMigrationSuffix = invokeMethod(config, "getSqlMigrationSuffix");
            this.sqlMigrationSuffixes = new String[] {sqlMigrationSuffix};
        }

        if (flywayVersion >= 50 && isFlywayPro) {
            this.undoSqlMigrationPrefix = invokeMethod(config, "getUndoSqlMigrationPrefix");
            this.errorHandlers = invokeMethod(config, "getErrorHandlers");
            this.dryRun = invokeMethod(config, "getDryRunOutput") != null;
        } else {
            this.undoSqlMigrationPrefix = null;
            this.errorHandlers = null;
            this.dryRun = false;
        }

        if (flywayVersion >= 51) {
            this.ignoreIgnoredMigrations = invokeMethod(config, "isIgnoreIgnoredMigrations");
        } else {
            this.ignoreIgnoredMigrations = false;
        }

        if (flywayVersion >= 51 && isFlywayPro) {
            this.errorOverrides = invokeMethod(config, "getErrorOverrides");
            this.stream = invokeMethod(config, "isStream");
            this.batch = invokeMethod(config, "isBatch");
            this.oracleSqlPlus = invokeMethod(config, "isOracleSqlplus");
        } else {
            this.errorOverrides = null;
            this.stream = false;
            this.batch = false;
            this.oracleSqlPlus = false;
        }

        if (flywayVersion >= 52) {
            this.ignorePendingMigrations = invokeMethod(config, "isIgnorePendingMigrations");
            this.connectRetries = invokeMethod(config, "getConnectRetries");
            this.initSql = invokeMethod(config, "getInitSql");
        } else {
            this.ignorePendingMigrations = false;
            this.connectRetries = 0;
            this.initSql = null;
        }

        if (flywayVersion >= 52 && isFlywayPro) {
            this.licenseKey = invokeMethod(config, "getLicenseKey");
        } else {
            this.licenseKey = null;
        }
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

    public MigrationResolver[] getResolvers() {
        return resolvers;
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

    public String[] getSqlMigrationSuffixes() {
        return sqlMigrationSuffixes;
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

    public MigrationVersion getTarget() {
        return target;
    }

    public String getTable() {
        return table;
    }

    public String[] getSchemas() {
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

    public Object[] getLocations() {
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

    public boolean isMixed() {
        return mixed;
    }

    public boolean isGroup() {
        return group;
    }

    public String getInstalledBy() {
        return installedBy;
    }

    public Object[] getErrorHandlers() {
        return errorHandlers;
    }

    public String[] getErrorOverrides() {
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

    public int getConnectRetries() {
        return connectRetries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlywayConfigSnapshot that = (FlywayConfigSnapshot) o;
        return skipDefaultResolvers == that.skipDefaultResolvers &&
                skipDefaultCallbacks == that.skipDefaultCallbacks &&
                placeholderReplacement == that.placeholderReplacement &&
                baselineOnMigrate == that.baselineOnMigrate &&
                outOfOrder == that.outOfOrder &&
                ignoreMissingMigrations == that.ignoreMissingMigrations &&
                ignoreIgnoredMigrations == that.ignoreIgnoredMigrations &&
                ignorePendingMigrations == that.ignorePendingMigrations &&
                ignoreFutureMigrations == that.ignoreFutureMigrations &&
                validateOnMigrate == that.validateOnMigrate &&
                cleanOnValidationError == that.cleanOnValidationError &&
                cleanDisabled == that.cleanDisabled &&
                allowMixedMigrations == that.allowMixedMigrations &&
                mixed == that.mixed &&
                group == that.group &&
                dryRun == that.dryRun &&
                stream == that.stream &&
                batch == that.batch &&
                oracleSqlPlus == that.oracleSqlPlus &&
                connectRetries == that.connectRetries &&
                Arrays.equals(resolvers, that.resolvers) &&
                Arrays.equals(errorHandlers, that.errorHandlers) &&
                Arrays.equals(locations, that.locations) &&
                Arrays.equals(schemas, that.schemas) &&
                Arrays.equals(sqlMigrationSuffixes, that.sqlMigrationSuffixes) &&
                Arrays.equals(errorOverrides, that.errorOverrides) &&
                Objects.equals(baselineVersion, that.baselineVersion) &&
                Objects.equals(target, that.target) &&
                Objects.equals(placeholders, that.placeholders) &&
                Objects.equals(table, that.table) &&
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
                Objects.equals(installedBy, that.installedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Arrays.hashCode(resolvers), Arrays.hashCode(errorHandlers),
                Arrays.hashCode(locations), Arrays.hashCode(schemas),
                Arrays.hashCode(sqlMigrationSuffixes), Arrays.hashCode(errorOverrides),
                baselineVersion, target, placeholders, table, baselineDescription,
                undoSqlMigrationPrefix, repeatableSqlMigrationPrefix,
                sqlMigrationSeparator, sqlMigrationPrefix, placeholderPrefix,
                placeholderSuffix, encoding, initSql, licenseKey,
                skipDefaultResolvers, skipDefaultCallbacks, placeholderReplacement, baselineOnMigrate,
                outOfOrder, ignoreMissingMigrations, ignoreIgnoredMigrations, ignorePendingMigrations,
                ignoreFutureMigrations, validateOnMigrate, cleanOnValidationError, cleanDisabled,
                allowMixedMigrations, mixed, group, installedBy,
                dryRun, stream, batch, oracleSqlPlus, connectRetries);
    }
}
