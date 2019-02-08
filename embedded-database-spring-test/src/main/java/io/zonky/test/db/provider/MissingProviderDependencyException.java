/*
 * Copyright 2019 the original author or authors.
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

package io.zonky.test.db.provider;

public class MissingProviderDependencyException extends MissingDatabaseProviderException {

    public MissingProviderDependencyException(DatabaseDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    public String getMessage() {
        ProviderType providerType = getDescriptor().getProviderType();
        String dependencyInfo = providerType.getDependencyInfo();

        if (dependencyInfo != null) {
            return String.format("You must add the following Maven dependency: '%s'", dependencyInfo);
        } else {
            return String.format("You must add a Maven dependency for '%s' provider", providerType);
        }
    }
}
