package io.zonky.test.db.provider;

import io.zonky.test.db.preparer.DatabasePreparer;

public interface DatabaseProvider {

    EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException;

}
