package io.zonky.test.db.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
class OnClassCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader();
        List<String> candidates = getCandidates(metadata, ConditionalOnClass.class);

        for (String className : candidates) {
            if (!ClassUtils.isPresent(className, classLoader)) {
                return false;
            }
        }

        return true;
    }

    private List<String> getCandidates(AnnotatedTypeMetadata metadata, Class<?> annotationType) {
        MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(annotationType.getName(), true);
        if (attributes == null || attributes.get("name") == null) {
            return Collections.emptyList();
        }
        return attributes.get("name").stream().flatMap(o -> Arrays.stream((String[]) o)).collect(Collectors.toList());
    }
}
