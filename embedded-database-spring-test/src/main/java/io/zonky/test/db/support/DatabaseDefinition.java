package io.zonky.test.db.support;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

import java.util.Objects;

public class DatabaseDefinition {

    private final String beanName;
    private final AutoConfigureEmbeddedDatabase.DatabaseType databaseType;
    private final AutoConfigureEmbeddedDatabase.DatabaseProvider providerType;

    public DatabaseDefinition(String beanName, AutoConfigureEmbeddedDatabase.DatabaseType databaseType, AutoConfigureEmbeddedDatabase.DatabaseProvider providerType) {
        this.beanName = beanName;
        this.databaseType = databaseType;
        this.providerType = providerType;
    }

    public String getBeanName() {
        return beanName;
    }

    public AutoConfigureEmbeddedDatabase.DatabaseType getDatabaseType() {
        return databaseType;
    }

    public AutoConfigureEmbeddedDatabase.DatabaseProvider getProviderType() {
        return providerType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseDefinition that = (DatabaseDefinition) o;
        return Objects.equals(beanName, that.beanName) &&
                databaseType == that.databaseType &&
                providerType == that.providerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(beanName, databaseType, providerType);
    }
}
