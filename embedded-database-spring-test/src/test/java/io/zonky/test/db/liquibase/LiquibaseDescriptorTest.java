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
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;

@Category(LiquibaseTestSuite.class)
public class LiquibaseDescriptorTest {

    @Test
    public void preservesCustomizer() throws Exception {
        Object customizer = newCustomizer();
        SpringLiquibase original = new SpringLiquibase();
        ReflectionUtils.invokeMethod(original, "setCustomizer", customizer);

        SpringLiquibase copy = new SpringLiquibase();
        LiquibaseDescriptor.from(original).applyTo(copy);

        assertThat((Object) ReflectionUtils.invokeMethod(copy, "getCustomizer")).isSameAs(customizer);
    }

    @Test
    public void customizerIsPartOfPreparerCacheIdentity() throws Exception {
        Object customizer = newCustomizer();
        SpringLiquibase first = new SpringLiquibase();
        SpringLiquibase second = new SpringLiquibase();
        first.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        second.setChangeLog(first.getChangeLog());
        LiquibaseDatabasePreparer withoutCustomizer = preparer(first);
        ReflectionUtils.invokeMethod(first, "setCustomizer", customizer);
        ReflectionUtils.invokeMethod(second, "setCustomizer", customizer);

        assertThat(preparer(first)).isEqualTo(preparer(second));
        assertThat(preparer(first).hashCode()).isEqualTo(preparer(second).hashCode());
        assertThat(preparer(first)).isNotEqualTo(withoutCustomizer);

        ReflectionUtils.invokeMethod(second, "setCustomizer", newCustomizer());
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

    private Object newCustomizer() throws Exception {
        // Compile against Liquibase 3.x; exercise the optional API on newer runtimes.
        assumeTrue(ClassUtils.isPresent("liquibase.integration.spring.Customizer", null));
        return mock(ClassUtils.forName("liquibase.integration.spring.Customizer", null));
    }

    private LiquibaseDatabasePreparer preparer(SpringLiquibase liquibase) {
        return new LiquibaseDatabasePreparer(LiquibaseDescriptor.from(liquibase));
    }
}
