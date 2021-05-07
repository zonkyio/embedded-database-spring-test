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

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.zonky.test.db.util.ReflectionUtils.invokeStaticMethod;
import static java.util.stream.Collectors.toCollection;

public class AnnotationUtils {

    private static final Class<?> testContextAnnotationUtilsClass;

    static {
        Class<?> targetClass;
        try {
            ClassLoader classLoader = AnnotationUtils.class.getClassLoader();
            targetClass = ClassUtils.forName("org.springframework.test.context.TestContextAnnotationUtils", classLoader);
        } catch (ClassNotFoundException e) {
            targetClass = null;
        }
        testContextAnnotationUtilsClass = targetClass;
    }

    private AnnotationUtils() {}

    public static Set<AutoConfigureEmbeddedDatabase> getDatabaseAnnotations(Class<?> annotatedElement) {
        Set<AutoConfigureEmbeddedDatabase> annotations;

        if (testContextAnnotationUtilsClass != null) {
            annotations = invokeStaticMethod(testContextAnnotationUtilsClass, "getMergedRepeatableAnnotations", annotatedElement, AutoConfigureEmbeddedDatabase.class);
        } else {
            annotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(annotatedElement, AutoConfigureEmbeddedDatabase.class);
        }

        return annotations.stream()
                .filter(distinctByKey(AutoConfigureEmbeddedDatabase::beanName))
                .collect(toCollection(LinkedHashSet::new));
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}
