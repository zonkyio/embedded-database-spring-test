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

package io.zonky.test.support;

import org.assertj.core.api.Condition;
import org.mockito.internal.util.MockUtil;

import static io.zonky.test.db.util.ReflectionUtils.invokeConstructor;
import static io.zonky.test.db.util.ReflectionUtils.invokeMethod;
import static io.zonky.test.db.util.ReflectionUtils.invokeStaticMethod;
import static org.assertj.core.api.Assertions.allOf;

public class MockitoAssertions {

    private MockitoAssertions() {}

    public static <T> Condition<T> mock() {
        return new Condition<T>("target object should be a mock") {
            @Override
            public boolean matches(T object) {
                try {
                    return invokeMethod(invokeConstructor(MockUtil.class), "isMock", object);
                } catch (Exception e) {
                    return invokeStaticMethod(MockUtil.class, "isMock", object);
                }
            }
        };
    }

    public static <T> Condition<T> mockWithName(String name) {
        Condition<T> hasName = new Condition<T>("mock should have a specified name") {
            @Override
            public boolean matches(T object) {
                try {
                    return name.equals(invokeMethod(invokeConstructor(MockUtil.class), "getMockName", object).toString());
                } catch (Exception e) {
                    return name.equals(invokeStaticMethod(MockUtil.class, "getMockName", object).toString());
                }
            }
        };

        return allOf(mock(), hasName);
    }
}
