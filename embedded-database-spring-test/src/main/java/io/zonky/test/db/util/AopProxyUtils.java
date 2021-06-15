/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.util;

import io.zonky.test.db.context.DatabaseTargetSource;
import io.zonky.test.db.context.DatabaseContext;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;

import javax.sql.DataSource;

public class AopProxyUtils {

    private AopProxyUtils() {}

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
