package io.zonky.test.db.preparer;

import io.zonky.test.db.provider.DatabasePreparer;
import org.springframework.aop.framework.ProxyFactory;

import javax.sql.DataSource;
import java.util.Optional;

public interface RecordingDataSource extends DataSource {

    Optional<DatabasePreparer> getPreparer();

    static RecordingDataSource wrap(DataSource dataSource) {
        ProxyFactory proxyFactory = new ProxyFactory(dataSource);
        proxyFactory.addAdvice(new RecordingMethodInterceptor());
        proxyFactory.addInterface(RecordingDataSource.class);
        proxyFactory.setProxyTargetClass(true);
        return (RecordingDataSource) proxyFactory.getProxy();
    }
}
