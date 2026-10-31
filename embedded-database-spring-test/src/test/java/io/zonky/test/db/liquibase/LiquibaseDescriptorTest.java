/*
 * Copyright 2026 the original author or authors.
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

package io.zonky.test.db.liquibase;

import io.zonky.test.category.LiquibaseTestSuite;
import io.zonky.test.db.util.ReflectionUtils;
import liquibase.integration.spring.SpringLiquibase;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.StubMethod;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.util.ClassUtils;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

@Category(LiquibaseTestSuite.class)
public class LiquibaseDescriptorTest {

    private static Class<?> customizerType;

    @Test
    public void preservesCustomizer() throws Exception {
        Object customizer = newCustomizer("customizer");
        SpringLiquibase original = new SpringLiquibase();
        ReflectionUtils.invokeMethod(original, "setCustomizer", customizer);

        SpringLiquibase copy = new SpringLiquibase();
        LiquibaseDescriptor.from(original).applyTo(copy);

        assertThat((Object) ReflectionUtils.invokeMethod(copy, "getCustomizer")).isSameAs(customizer);
    }

    @Test
    public void customizerIsPartOfPreparerCacheIdentity() throws Exception {
        SpringLiquibase first = new SpringLiquibase();
        SpringLiquibase second = new SpringLiquibase();
        first.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        second.setChangeLog(first.getChangeLog());
        LiquibaseDatabasePreparer withoutCustomizer = preparer(first);
        // Distinct but equivalent instances, as created by separate application contexts
        ReflectionUtils.invokeMethod(first, "setCustomizer", newCustomizer("first"));
        ReflectionUtils.invokeMethod(second, "setCustomizer", newCustomizer("first"));

        assertThat(preparer(first)).isEqualTo(preparer(second));
        assertThat(preparer(first).hashCode()).isEqualTo(preparer(second).hashCode());
        assertThat(preparer(first)).isNotEqualTo(withoutCustomizer);

        ReflectionUtils.invokeMethod(second, "setCustomizer", newCustomizer("second"));
        assertThat(preparer(first)).isNotEqualTo(preparer(second));
    }

    @Test
    public void copiesConfigurationWithoutCustomizer() {
        SpringLiquibase original = new SpringLiquibase();
        original.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        SpringLiquibase copy = new SpringLiquibase();

        LiquibaseDescriptor.from(original).applyTo(copy);

        assertThat(LiquibaseDescriptor.from(copy)).isEqualTo(LiquibaseDescriptor.from(original));
    }

    private Object newCustomizer(String value) throws Exception {
        // Compile against Liquibase 3.x; exercise the optional API on newer runtimes.
        assumeTrue(ClassUtils.isPresent("liquibase.integration.spring.Customizer", null));
        if (customizerType == null) {
            // Unlike a mock, it has no equals() of its own, like a typical lambda or bean customizer
            customizerType = new ByteBuddy()
                    .subclass(Object.class)
                    .implement(ClassUtils.forName("liquibase.integration.spring.Customizer", null))
                    .defineField("value", String.class, Visibility.PUBLIC)
                    .method(named("customize")).intercept(StubMethod.INSTANCE)
                    .make()
                    .load(getClass().getClassLoader())
                    .getLoaded();
        }
        Object customizer = customizerType.getDeclaredConstructor().newInstance();
        customizerType.getField("value").set(customizer, value);
        return customizer;
    }

    private LiquibaseDatabasePreparer preparer(SpringLiquibase liquibase) {
        return new LiquibaseDatabasePreparer(LiquibaseDescriptor.from(liquibase));
    }
}
