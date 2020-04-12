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

package io.zonky.test.db.provider;

import org.springframework.core.NestedRuntimeException;

public class ProviderException extends NestedRuntimeException {

    /**
     * Construct a new provider exception.
     *
     * @param message the exception message
     */
    public ProviderException(String message) {
        super(message);
    }

    /**
     * Construct a new provider exception.
     *
     * @param message the exception message
     * @param cause the cause
     */
    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
