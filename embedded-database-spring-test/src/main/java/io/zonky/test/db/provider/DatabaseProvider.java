package io.zonky.test.db.provider;

public interface DatabaseProvider {

    EmbeddedDatabase createDatabase(DatabasePreparer preparer) throws ProviderException;

}
