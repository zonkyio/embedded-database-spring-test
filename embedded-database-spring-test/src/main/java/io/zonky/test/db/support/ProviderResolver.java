package io.zonky.test.db.support;

public interface ProviderResolver {

    ProviderDescriptor getDescriptor(DatabaseDefinition definition);

}
