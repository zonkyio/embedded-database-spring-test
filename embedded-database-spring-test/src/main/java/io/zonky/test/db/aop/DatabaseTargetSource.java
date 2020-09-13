package io.zonky.test.db.aop;

import io.zonky.test.db.context.DataSourceContext;
import org.springframework.aop.TargetSource;

import javax.sql.DataSource;

public class DatabaseTargetSource implements TargetSource {

    private final DataSourceContext context;

    public DatabaseTargetSource(DataSourceContext context) {
        this.context = context;
    }

    public DataSourceContext getContext() {
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
