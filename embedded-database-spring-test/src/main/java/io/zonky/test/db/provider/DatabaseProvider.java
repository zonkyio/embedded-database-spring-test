package io.zonky.test.db.provider;

public interface DatabaseProvider {

    DatabaseResult createDatabase(DatabasePreparer preparer) throws Exception;

}
