package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import io.zonky.test.db.provider.DatabasePreparer;

public class CompositeDatabasePreparer implements DatabasePreparer {

    private final List<DatabasePreparer> preparers;

    public CompositeDatabasePreparer(List<DatabasePreparer> preparers) {
        this.preparers = ImmutableList.copyOf(preparers);
    }

    public List<DatabasePreparer> getPreparers() {
        return preparers;
    }

    @Override
    public void prepare(DataSource dataSource) throws SQLException {
        for (DatabasePreparer preparer : preparers) {
            preparer.prepare(dataSource);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeDatabasePreparer that = (CompositeDatabasePreparer) o;
        return Objects.equals(preparers, that.preparers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preparers);
    }
}
