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

package io.zonky.test.assertj;

import org.assertj.core.api.Condition;
import org.mockito.internal.util.MockUtil;

import static org.assertj.core.api.Assertions.allOf;

public class MockitoAssertions {

    private MockitoAssertions() {}

    public static <T> Condition<T> mockWithName(String name) {
        MockUtil mockUtil = new MockUtil();

        Condition<T> isMock = new Condition<T>("target object should be a mock") {
            @Override
            public boolean matches(T object) {
                return mockUtil.isMock(object);
            }
        };
        Condition<T> hasName = new Condition<T>("mock should have a specified name") {
            @Override
            public boolean matches(T object) {
                return name.equals(mockUtil.getMockName(object).toString());
            }
        };

        return allOf(isMock, hasName);
    }
}
