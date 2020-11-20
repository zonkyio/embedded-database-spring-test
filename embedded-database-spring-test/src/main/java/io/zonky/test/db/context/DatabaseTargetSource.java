package io.zonky.test.db.context;

import io.zonky.test.db.context.DatabaseContext;
import org.springframework.aop.TargetSource;

import javax.sql.DataSource;

public class DatabaseTargetSource implements TargetSource {

    private final DatabaseContext context;

    public DatabaseTargetSource(DatabaseContext context) {
        this.context = context;
    }

    public DatabaseContext getContext() {
        return context;
    }

    @Override
    public Class<?> getTargetClass() {
        return DataSource.class;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public Object getTarget() {
        return context.getDatabase();
    }

    @Override
    public void releaseTarget(Object target) {
        // nothing to do
    }
}
