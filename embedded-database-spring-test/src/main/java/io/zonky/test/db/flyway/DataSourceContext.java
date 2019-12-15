package io.zonky.test.db.flyway;

import io.zonky.test.db.provider.DatabaseDescriptor;
import io.zonky.test.db.provider.DatabasePreparer;
import org.springframework.aop.TargetSource;

// TODO: move into a different package
public interface DataSourceContext extends TargetSource {

    void setDescriptor(DatabaseDescriptor descriptor);

    void reset();

    void apply(DatabasePreparer preparer);

}
