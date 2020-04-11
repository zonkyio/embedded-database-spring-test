package io.zonky.test.db.preparer;

import org.springframework.aop.framework.ProxyFactory;

import javax.sql.DataSource;

public interface RecordingDataSource extends DataSource {

    ReplayableDatabasePreparer getPreparer();

    static RecordingDataSource wrap(DataSource dataSource) {
        ProxyFactory proxyFactory = new ProxyFactory(dataSource);
        proxyFactory.addAdvice(new RecordingMethodInterceptor());
        proxyFactory.addInterface(RecordingDataSource.class);
        proxyFactory.setProxyTargetClass(true);
        return (RecordingDataSource) proxyFactory.getProxy();
    }

}
