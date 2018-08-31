/*
 * Copyright 2016 the original author or authors.
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

package io.zonky.test.db.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.aop.TargetSource;
import org.springframework.util.concurrent.ListenableFuture;

import javax.sql.DataSource;

/**
 * Interface extending {@link TargetSource} that is used by {@link io.zonky.test.db.postgres.FlywayEmbeddedPostgresDataSourceFactoryBean}
 * for deferred initialization of the embedded database until the application context is fully loaded and the flyway bean is available.
 */
public interface FlywayDataSourceContext extends TargetSource {

    ListenableFuture<DataSource> reload(Flyway flyway);

}
