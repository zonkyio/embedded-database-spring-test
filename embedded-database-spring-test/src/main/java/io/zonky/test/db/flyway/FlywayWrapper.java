package io.zonky.test.db.flyway;

import org.aopalliance.intercept.MethodInterceptor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.database.DatabaseFactory;
import org.flywaydb.core.internal.util.scanner.Scanner;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.AopTestUtils;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;

import static org.apache.commons.lang3.reflect.MethodUtils.invokeStaticMethod;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.invokeMethod;

public class FlywayWrapper {

    private static final ClassLoader classLoader = FlywayContextExtension.class.getClassLoader();
    private static final int flywayVersion = FlywayClassUtils.getFlywayVersion();

    private final Flyway flyway;

    public FlywayWrapper(Flyway flyway) {
        this.flyway = flyway;
    }

    public Flyway getFlyway() {
        return flyway;
    }

    public DataSourceContext getDataSourceContext() {
        DataSource dataSource = flyway.getDataSource();

        if (dataSource instanceof Advised) {
            TargetSource targetSource = ((Advised) dataSource).getTargetSource();
            if (targetSource instanceof DataSourceContext) {
                return (DataSourceContext) targetSource;
            }
        }

        throw new IllegalStateException("Data source context cannot be resolved");
    }

    public Collection<ResolvedMigration> getMigrations() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        MigrationResolver resolver = createMigrationResolver(flyway);

        if (flywayVersion >= 52) {
            Object configInstance = getField(flyway, "configuration");
            Class<?> contextType = ClassUtils.forName("org.flywaydb.core.api.resolver.Context", classLoader);
            Object contextInstance = ProxyFactory.getProxy(contextType, (MethodInterceptor) invocation ->
                    "getConfiguration".equals(invocation.getMethod().getName()) ? configInstance : invocation.proceed());
            return invokeMethod(resolver, "resolveMigrations", contextInstance);
        } else {
            return resolver.resolveMigrations();
        }
    }

    private MigrationResolver createMigrationResolver(Flyway flyway) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        flyway = AopTestUtils.getUltimateTargetObject(flyway);

        if (flywayVersion >= 52) {
            Object configuration = getField(flyway, "configuration");
            Object database = invokeStaticMethod(DatabaseFactory.class, "createDatabase", flyway, false);
            Object factory = invokeMethod(database, "createSqlStatementBuilderFactory");
            Class<?> scannerType = ClassUtils.forName("org.flywaydb.core.internal.scanner.Scanner", classLoader);
            Object scanner = scannerType.getConstructors()[0].newInstance(
                    Arrays.asList((Object[]) invokeMethod(configuration, "getLocations")),
                    invokeMethod(configuration, "getClassLoader"),
                    invokeMethod(configuration, "getEncoding"));
            return invokeMethod(flyway, "createMigrationResolver", database, scanner, scanner, factory);
        } else if (flywayVersion >= 51) {
            Object configuration = getField(flyway, "configuration");
            Object scanner = Scanner.class.getConstructors()[0].newInstance(configuration);
            Object placeholderReplacer = invokeMethod(flyway, "createPlaceholderReplacer");
            return invokeMethod(flyway, "createMigrationResolver", null, scanner, placeholderReplacer);
        } else if (flywayVersion >= 40) {
            Scanner scanner = new Scanner(flyway.getClassLoader());
            return invokeMethod(flyway, "createMigrationResolver", null, scanner);
        } else {
            return invokeMethod(flyway, "createMigrationResolver", (Object) null);
        }
    }

    public String[] getLocations() {
        if (flywayVersion >= 51) {
            Object configuration = getField(flyway, "configuration");
            return Arrays.stream((Object[]) invokeMethod(configuration, "getLocations"))
                    .map(location -> invokeMethod(location, "getDescriptor"))
                    .toArray(String[]::new);
        } else {
            return flyway.getLocations();
        }
    }

    public void setLocations(String[] locations) {
        if (flywayVersion >= 51) {
            Object configuration = getField(flyway, "configuration");
            invokeMethod(configuration, "setLocationsAsStrings", (Object) locations);
        } else {
            flyway.setLocations(locations);
        }
    }
}
