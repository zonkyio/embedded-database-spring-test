package io.zonky.test.db.flyway;

import com.google.common.collect.Lists;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.NameMatchMethodPointcutAdvisor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.zonky.test.db.flyway.operation.CleanFlywayContextOperation;
import io.zonky.test.db.flyway.operation.MigrateFlywayContextOperation;
import io.zonky.test.db.flyway.preparer.BaselineFlywayDatabasePreparer;

public class FlywayContextExtension implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AopInfrastructureBean) {
            return bean;
        }

        if (bean instanceof Flyway) {
            Advice advice = new FlywayContextExtensionInterceptor((Flyway) bean);
            NameMatchMethodPointcutAdvisor advisor = new NameMatchMethodPointcutAdvisor(advice);
            advisor.setMappedNames("clean", "baseline", "migrate");

            if (bean instanceof Advised && !((Advised) bean).isFrozen()) {
                ((Advised) bean).addAdvisor(0, advisor);
                return bean;
            } else {
                ProxyFactory proxyFactory = new ProxyFactory(bean);
                proxyFactory.setProxyTargetClass(true);
                proxyFactory.addAdvisor(advisor);
                return proxyFactory.getProxy();
            }
        }

        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    protected class FlywayContextExtensionInterceptor implements MethodInterceptor {

        private final Lock lock = new ReentrantLock(true);

        private final DataSourceContext dataSourceContext;

        private final String[] defaultLocations;

        private final Flyway flywayBean;

        private final FlywayAdapter flywayAdapter;

        FlywayContextExtensionInterceptor(Flyway flywayBean) {
            this.flywayBean = flywayBean;
            this.flywayAdapter = new FlywayAdapter(flywayBean);
            this.defaultLocations = flywayAdapter.getLocations();

            DataSource dataSource = flywayBean.getDataSource();
            this.dataSourceContext = getDataSourceContext(dataSource).orElse(null); // TODO: fix potential NPEs
        }

        // TODO: extract to utils class
        private Optional<DataSourceContext> getDataSourceContext(DataSource dataSource) {
            if (dataSource instanceof Advised) {
                TargetSource targetSource = ((Advised) dataSource).getTargetSource();
                if (targetSource instanceof DataSourceContext) {
                    return Optional.of((DataSourceContext) targetSource);
                }
            }
            return Optional.empty();
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            lock.lock();
            try {
                String methodName = invocation.getMethod().getName();

                if (StringUtils.equals(methodName, "clean")) {
                    dataSourceContext.apply(new CleanFlywayContextOperation(flywayBean));
                } else if (StringUtils.equals(methodName, "baseline")) {
                    if (dataSourceContext.isInitialized()) {
                        flywayBean.baseline(); // TODO:
                    } else {
                        dataSourceContext.apply(new DataSourceContext.PreparerOperation(new BaselineFlywayDatabasePreparer(flywayBean))); // TODO
                    }
                } else if (StringUtils.equals(methodName, "migrate")) {
                    MigrateFlywayContextOperation migrateOperation = new MigrateFlywayContextOperation(flywayBean);

                    if (isAppendable(flywayAdapter, defaultLocations)) {
                        String[] oldLocations = flywayAdapter.getLocations();
                        try {
                            flywayAdapter.setLocations(defaultLocations);
                            dataSourceContext.apply(migrateOperation);
                        } finally {
                            flywayAdapter.setLocations(oldLocations);
                        }
                        int appliedMigrations = migrateOperation.getResult().isDone() ? migrateOperation.getResult().get() : 0;
                        return appliedMigrations + flywayBean.migrate(); // TODO: try to use baseline to skip verification
                    } else {
                        dataSourceContext.apply(migrateOperation);
                        return migrateOperation.getResult().isDone() ? migrateOperation.getResult().get() : 0;
                    }
                } else {
                    return invocation.proceed();
                }

            } finally {
                lock.unlock();
            }

            return null;
        }
    }

    /**
     * Checks if test migrations are appendable to core migrations.
     */
    protected boolean isAppendable(FlywayAdapter flyway, String[] defaultLocations) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        String[] flywayLocations = flyway.getLocations();

        if (!Arrays.asList(flywayLocations).containsAll(Arrays.asList(defaultLocations))) {
            return false;
        }

        // TODO
        List<String> testLocations0 = Lists.newArrayList(flywayLocations);
        testLocations0.removeAll(Arrays.asList(defaultLocations));
        String[] testLocations = testLocations0.toArray(new String[0]);

        if (testLocations.length == 0) {
            return false;
        }

        MigrationVersion testFirstVersion = findFirstVersion(flyway, testLocations);
        if (testFirstVersion == MigrationVersion.EMPTY) {
            return true;
        }

        MigrationVersion coreLastVersion = findLastVersion(flyway, defaultLocations);
        return coreLastVersion.compareTo(testFirstVersion) < 0;
    }

    protected MigrationVersion findFirstVersion(FlywayAdapter flyway, String... locations) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Collection<ResolvedMigration> migrations = resolveMigrations(flyway, locations);
        return migrations.stream()
                .filter(migration -> migration.getVersion() != null)
                .findFirst()
                .map(ResolvedMigration::getVersion)
                .orElse(MigrationVersion.EMPTY);
    }

    protected MigrationVersion findLastVersion(FlywayAdapter flyway, String... locations) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Collection<ResolvedMigration> migrations = resolveMigrations(flyway, locations);
        return migrations.stream()
                .filter(migration -> migration.getVersion() != null)
                .reduce((first, second) -> second) // finds last item
                .map(ResolvedMigration::getVersion)
                .orElse(MigrationVersion.EMPTY);
    }

    protected Collection<ResolvedMigration> resolveMigrations(FlywayAdapter flyway, String[] locations) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        String[] oldLocations = flyway.getLocations();
        try {
            flyway.setLocations(locations);
            return flyway.getMigrations();
        } finally {
            flyway.setLocations(oldLocations);
        }
    }
}
