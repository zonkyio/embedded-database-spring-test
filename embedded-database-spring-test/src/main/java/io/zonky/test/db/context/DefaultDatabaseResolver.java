package io.zonky.test.db.context;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import io.zonky.test.db.EmbeddedDatabaseContextCustomizerFactory.DatabaseDefinition;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.Set;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.DEFAULT;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.AUTO;

public class DefaultDatabaseResolver implements DatabaseResolver {

    private final Environment environment;

    public DefaultDatabaseResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public DatabaseDescriptor getDescriptor(DatabaseDefinition definition) {
        // TODO: DatabaseDefinition#beanName may not be initialized properly
        String providerName = getProviderName(definition.getProviderType());
        String databaseName = getDatabaseName(definition.getDatabaseType());
        return DatabaseDescriptor.of(databaseName, providerName);
    }

    protected String getProviderName(DatabaseProvider providerType) {
        return providerType != DEFAULT ? providerType.name() :
                environment.getProperty("zonky.test.database.provider", "docker");
    }

    protected String getDatabaseName(DatabaseType databaseType) {
        if (databaseType != AUTO) {
            return databaseType.name();
        }

        String databaseName = environment.getProperty("zonky.test.database.type");
        if (databaseName != null) {
            return databaseName;
        }

        Set<DatabaseType> detectedTypes = new LinkedHashSet<>();

        if (ClassUtils.isPresent("org.postgresql.ds.PGSimpleDataSource", null)) {
            detectedTypes.add(DatabaseType.POSTGRES);
        }
        if (ClassUtils.isPresent("com.microsoft.sqlserver.jdbc.SQLServerDataSource", null)) {
            detectedTypes.add(DatabaseType.MSSQL);
        }
        if (ClassUtils.isPresent("com.mysql.cj.jdbc.MysqlDataSource", null)) {
            detectedTypes.add(DatabaseType.MYSQL);
        }
        if (ClassUtils.isPresent("org.mariadb.jdbc.MariaDbDataSource", null)) {
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
