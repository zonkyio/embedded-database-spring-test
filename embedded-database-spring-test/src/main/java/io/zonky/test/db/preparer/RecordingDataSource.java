package io.zonky.test.db.preparer;

import org.springframework.aop.framework.ProxyFactory;

import javax.sql.DataSource;

import io.zonky.test.db.provider.DatabasePreparer;

public interface RecordingDataSource extends DataSource {

    DatabasePreparer buildPreparer(); // TODO: consider using Optional type

    static RecordingDataSource wrap(DataSource dataSource) {
        ProxyFactory proxyFactory = new ProxyFactory(dataSource);
        proxyFactory.addAdvice(new RecordingMethodInterceptor());
        proxyFactory.addInterface(RecordingDataSource.class);
        return (RecordingDataSource) proxyFactory.getProxy();
    }
}
