package io.zonky.test.db.context;

import io.zonky.test.db.preparer.DatabasePreparer;
import org.springframework.aop.TargetSource;

public interface DataSourceContext extends TargetSource {

    void setDescriptor(DatabaseDescriptor descriptor);

    State getState();

    void reset();

    void apply(DatabasePreparer preparer);

    enum State {

        INITIALIZING,
        FRESH,
        AHEAD,
        DIRTY
    }
}
