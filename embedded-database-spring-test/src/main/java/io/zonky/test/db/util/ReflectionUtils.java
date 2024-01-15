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

package io.zonky.test.db.util;

import org.springframework.test.util.AopTestUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MethodInvoker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.stream.IntStream;

public class ReflectionUtils {

    private ReflectionUtils() {}

    @SuppressWarnings("unchecked")
    public static <T> T getField(Object targetObject, String name) {
        Assert.notNull(targetObject, "Target object must not be null");

        targetObject = AopTestUtils.getUltimateTargetObject(targetObject);
        Class<?> targetClass = targetObject.getClass();

        Field field = org.springframework.util.ReflectionUtils.findField(targetClass, name);
        if (field == null) {
            throw new IllegalArgumentException(String.format("Could not find field '%s' on %s", name, safeToString(targetObject)));
        }

        org.springframework.util.ReflectionUtils.makeAccessible(field);
        return (T) org.springframework.util.ReflectionUtils.getField(field, targetObject);
    }

    @SuppressWarnings("unchecked")
    public static void setField(Object targetObject, String name, Object value) {
        Assert.notNull(targetObject, "Target object must not be null");

        targetObject = AopTestUtils.getUltimateTargetObject(targetObject);
        Class<?> targetClass = targetObject.getClass();

        Field field = org.springframework.util.ReflectionUtils.findField(targetClass, name);
        if (field == null) {
            throw new IllegalArgumentException(String.format("Could not find field '%s' on %s", name, safeToString(targetObject)));
        }

        org.springframework.util.ReflectionUtils.makeAccessible(field);
        org.springframework.util.ReflectionUtils.setField(field, targetObject, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Object targetObject, String name, Object... args) {
        Assert.notNull(targetObject, "Target object must not be null");
        Assert.hasText(name, "Method name must not be empty");

        try {
            MethodInvoker methodInvoker = new MethodInvoker();
            methodInvoker.setTargetObject(targetObject);
            methodInvoker.setTargetMethod(name);
            methodInvoker.setArguments(args);
            methodInvoker.prepare();
            return (T) methodInvoker.invoke();
        } catch (Exception ex) {
            org.springframework.util.ReflectionUtils.handleReflectionException(ex);
            throw new IllegalStateException("Should never get here");
        }
    }

    // TODO:
    public static boolean hasField(String className, String name) throws ClassNotFoundException {
        Assert.notNull(className, "Target class must not be null");

        Class<?> targetClass = ClassUtils.forName(className, null);
        return org.springframework.util.ReflectionUtils.findField(targetClass, name) != null;
    }

    public static <T> T invokeStaticMethod(String className, String name, Object... args) throws ClassNotFoundException {
        Assert.notNull(className, "Target class must not be null");

        Class<?> targetClass = ClassUtils.forName(className, null);
        return invokeStaticMethod(targetClass, name, args);
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeStaticMethod(Class<?> targetClass, String name, Object... args) {
        Assert.notNull(targetClass, "Target class must not be null");
        Assert.hasText(name, "Method name must not be empty");

        try {
            MethodInvoker methodInvoker = new MethodInvoker();
            methodInvoker.setTargetClass(targetClass);
            methodInvoker.setTargetMethod(name);
            methodInvoker.setArguments(args);
            methodInvoker.prepare();
            return (T) methodInvoker.invoke();
        } catch (Exception ex) {
            org.springframework.util.ReflectionUtils.handleReflectionException(ex);
            throw new IllegalStateException("Should never get here");
        }
    }

    public static <T> T invokeConstructor(String className, Object... args) throws ClassNotFoundException {
        Assert.notNull(className, "Target class must not be null");

        Class<?> targetClass = ClassUtils.forName(className, null);
        return invokeConstructor(targetClass, args);
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeConstructor(Class<?> targetClass, Object... args) {
        Assert.notNull(targetClass, "Target class must not be null");

        try {
            for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() != args.length) {
                    continue;
                }
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                boolean parametersMatches = IntStream.range(0, args.length)
                        .allMatch(i -> ClassUtils.isAssignableValue(parameterTypes[i], args[i]));
                if (parametersMatches) {
                    org.springframework.util.ReflectionUtils.makeAccessible(constructor);
                    return (T) constructor.newInstance(args);
                }
            }
            throw new IllegalArgumentException(String.format("Could not find constructor on %s", targetClass));
        } catch (Exception ex) {
            org.springframework.util.ReflectionUtils.handleReflectionException(ex);
            throw new IllegalStateException("Should never get here");
        }
    }

    private static String safeToString(Object target) {
        try {
            return String.format("target object [%s]", target);
        } catch (Exception ex) {
            return String.format("target of type [%s] whose toString() method threw [%s]",
                    (target != null ? target.getClass().getName() : "unknown"), ex);
        }
    }
}
