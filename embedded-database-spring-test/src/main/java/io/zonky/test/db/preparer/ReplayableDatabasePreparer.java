package io.zonky.test.db.preparer;

public interface ReplayableDatabasePreparer extends DatabasePreparer {

    boolean hasRecords();

}
