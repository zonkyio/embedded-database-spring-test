package io.zonky.test.db.support;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultProviderResolver implements ProviderResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProviderResolver.class);

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

        if (providerName == null) {
            if (isEmbeddedDatabase(databaseName)) {
                providerName = DatabaseProvider.EMBEDDED.name();
            } else {
                providerName = DatabaseProvider.DOCKER.name();
            }
        }

        ProviderDescriptor descriptor = ProviderDescriptor.of(providerName, databaseName);

        if (StringUtils.hasText(definition.getBeanName())) {
            logger.debug("Descriptor {} for '{}' DataSource has been resolved", descriptor, definition.getBeanName());
        } else {
            logger.debug("Descriptor {} for default DataSource has been resolved", descriptor);
        }

        return descriptor;
    }

    protected boolean isEmbeddedDatabase(String databaseType) {
        return DatabaseType.H2.name().equalsIgnoreCase(databaseType) ||
                DatabaseType.HSQL.name().equalsIgnoreCase(databaseType) ||
                DatabaseType.DERBY.name().equalsIgnoreCase(databaseType);
    }

    protected String getProviderName(DatabaseProvider providerType) {
        if (providerType != DatabaseProvider.DEFAULT) {
            return providerType.name();
        }

        String providerName = environment.getProperty("zonky.test.database.provider");
        if (providerName != null && !providerName.equalsIgnoreCase(DatabaseProvider.DEFAULT.name())) {
            return providerName;
        }

        return null;
    }

    protected String getDatabaseName(DatabaseType databaseType) {
        if (databaseType != DatabaseType.AUTO) {
            return databaseType.name();
        }

        String databaseName = environment.getProperty("zonky.test.database.type");
        if (databaseName != null && !databaseName.equalsIgnoreCase(DatabaseType.AUTO.name())) {
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
        if (ClassUtils.isPresent("org.h2.Driver", classLoader)) {
            detectedTypes.add(DatabaseType.H2);
        }
        if (ClassUtils.isPresent("org.hsqldb.jdbcDriver", classLoader)) {
            detectedTypes.add(DatabaseType.HSQL);
        }
        if (ClassUtils.isPresent("org.apache.derby.jdbc.EmbeddedDriver", classLoader)) {
            detectedTypes.add(DatabaseType.DERBY);
        }

        if (detectedTypes.isEmpty()) {
            throw new IllegalStateException("Database auto-detection failed, no database driver detected. " +
                    "Please add a corresponding Maven or Gradle dependency to your project.");
        }
        if (detectedTypes.size() > 1) {
            throw new IllegalStateException("Database auto-detection failed, multiple database drivers detected: " + detectedTypes + ". " +
                    "You have to specify the database type manually via @AutoConfigureEmbeddedDatabase or using configuration properties.");
        }

        return detectedTypes.iterator().next().name();
    }
}
