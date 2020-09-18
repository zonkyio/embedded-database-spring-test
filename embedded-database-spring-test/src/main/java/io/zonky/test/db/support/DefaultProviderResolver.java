package io.zonky.test.db.support;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.Set;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.DEFAULT;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.AUTO;

public class DefaultProviderResolver implements ProviderResolver {

    private final Environment environment;
    private final ClassLoader classLoader;

    public DefaultProviderResolver(Environment environment, ClassLoader classLoader) {
        this.environment = environment;
        this.classLoader = classLoader;
    }

    @Override
    public ProviderDescriptor getDescriptor(DatabaseDefinition definition) {
        String providerName = getProviderName(definition.getProviderType());
        String databaseName = getDatabaseName(definition.getDatabaseType());
        return ProviderDescriptor.of(providerName, databaseName);
    }

    protected String getProviderName(DatabaseProvider providerType) {
        if (providerType != DEFAULT) {
            return providerType.name();
        }

        String providerName = environment.getProperty("zonky.test.database.provider");
        if (providerName != null && !providerName.equalsIgnoreCase(DEFAULT.name())) {
            return providerName;
        }

        return DOCKER.name();
    }

    protected String getDatabaseName(DatabaseType databaseType) {
        if (databaseType != AUTO) {
            return databaseType.name();
        }

        String databaseName = environment.getProperty("zonky.test.database.type");
        if (databaseName != null && !databaseName.equalsIgnoreCase(AUTO.name())) {
            return databaseName;
        }

        Set<DatabaseType> detectedTypes = new LinkedHashSet<>();

        if (ClassUtils.isPresent("org.postgresql.ds.PGSimpleDataSource", classLoader)) {
            detectedTypes.add(DatabaseType.POSTGRES);
        }
        if (ClassUtils.isPresent("com.microsoft.sqlserver.jdbc.SQLServerDataSource", classLoader)) {
            detectedTypes.add(DatabaseType.MSSQL);
        }
        if (ClassUtils.isPresent("com.mysql.cj.jdbc.MysqlDataSource", classLoader)) {
            detectedTypes.add(DatabaseType.MYSQL);
        }
        if (ClassUtils.isPresent("org.mariadb.jdbc.MariaDbDataSource", classLoader)) {
            detectedTypes.add(DatabaseType.MARIADB);
        }

        if (detectedTypes.isEmpty()) {
            throw new IllegalStateException("Database auto-detection failed, no database detected. Set a database type manually or add an appropriate Maven or Gradle dependency.");
        }
        if (detectedTypes.size() > 1) {
            throw new IllegalStateException("Database auto-detection failed, multiple databases detected: " + detectedTypes);
        }

        return detectedTypes.iterator().next().name();
    }
}
