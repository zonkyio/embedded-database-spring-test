/*
 * Copyright 2020 the original author or authors.
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

package io.zonky.test.db.flyway.preparer;

import io.zonky.test.db.flyway.FlywayDescriptor;
import io.zonky.test.db.flyway.FlywayWrapper;

public class CleanFlywayDatabasePreparer extends FlywayDatabasePreparer {

    public CleanFlywayDatabasePreparer(FlywayDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    public long estimatedDuration() {
        return 100;
    }

    @Override
    protected Object doOperation(FlywayWrapper wrapper) {
        wrapper.setCleanDisabled(false);
        return wrapper.clean();
    }
}
