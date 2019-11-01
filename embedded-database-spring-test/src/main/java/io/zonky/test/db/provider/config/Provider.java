package io.zonky.test.db.provider.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Lazy
@Qualifier
public @interface Provider {

    String type();

    String database();

}
