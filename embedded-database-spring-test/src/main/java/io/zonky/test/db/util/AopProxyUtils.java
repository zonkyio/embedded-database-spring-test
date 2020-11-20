package io.zonky.test.db.util;

import io.zonky.test.db.context.DatabaseTargetSource;
import io.zonky.test.db.context.DatabaseContext;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;

import javax.sql.DataSource;

public class AopProxyUtils {

    public static DatabaseContext getDatabaseContext(DataSource dataSource) {
        if (dataSource instanceof Advised) {
            TargetSource targetSource = ((Advised) dataSource).getTargetSource();
            if (targetSource instanceof DatabaseTargetSource) {
                return ((DatabaseTargetSource) targetSource).getContext();
            }
        }
        return null;
    }
}
