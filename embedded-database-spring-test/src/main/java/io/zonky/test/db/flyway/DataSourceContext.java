package io.zonky.test.db.flyway;

import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import org.springframework.aop.TargetSource;

// TODO: move into a different package
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
