package io.zonky.test.db.context;

import io.zonky.test.db.EmbeddedDatabaseContextCustomizerFactory.DatabaseDefinition;

public interface DatabaseResolver {

    DatabaseDescriptor getDescriptor(DatabaseDefinition definition);

}
