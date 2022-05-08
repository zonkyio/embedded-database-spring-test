/*
 * Copyright 2022 the original author or authors.
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

import org.springframework.util.ObjectUtils;

public class StringUtils {

    private StringUtils() {}

    public static boolean startsWithAny(String input, String... searchStrings) {
        if (org.springframework.util.StringUtils.isEmpty(input) || ObjectUtils.isEmpty(searchStrings)) {
            return false;
        }
        for (String searchString : searchStrings) {
            if (input.startsWith(searchString)) {
                return true;
            }
        }
        return false;
    }

    public static boolean endsWithAny(String input, String... searchStrings) {
        if (org.springframework.util.StringUtils.isEmpty(input) || ObjectUtils.isEmpty(searchStrings)) {
            return false;
        }
        for (String searchString : searchStrings) {
            if (input.endsWith(searchString)) {
                return true;
            }
        }
        return false;
    }
}
