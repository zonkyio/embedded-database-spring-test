/*
 * Copyright 2023 the original author or authors.
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

package io.zonky.test.db.flyway;

import java.util.Arrays;
import java.util.stream.Collectors;

public class FlywayVersion implements Comparable<FlywayVersion> {

    private final int[] parts;

    public static FlywayVersion parseVersion(String version) {
        String[] split = version.split("\\.");
        int[] parts = Arrays.stream(split).mapToInt(Integer::parseInt).toArray();
        return new FlywayVersion(parts);
    }

    private FlywayVersion(int[] parts) {
        this.parts = parts;
    }

    public boolean isGreaterThan(String version) {
        return compareTo(parseVersion(version)) > 0;
    }

    public boolean isGreaterThanOrEqualTo(String version) {
        return compareTo(parseVersion(version)) >= 0;
    }

    public boolean isLessThan(String version) {
        return compareTo(parseVersion(version)) < 0;
    }

    public boolean isLessThanOrEqualTo(String version) {
        return compareTo(parseVersion(version)) <= 0;
    }

    @Override
    public int compareTo(FlywayVersion other) {
        for (int i = 0; i < this.parts.length || i < other.parts.length; i++) {
            int thisPart = i < this.parts.length ? this.parts[i] : 0;
            int otherPart = i < other.parts.length ? other.parts[i] : 0;

            if (thisPart > otherPart) {
                return 1;
            } else if (thisPart < otherPart) {
                return -1;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlywayVersion that = (FlywayVersion) o;
        return Arrays.equals(parts, that.parts);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(parts);
    }

    @Override
    public String toString() {
        return Arrays.stream(parts)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining("."));
    }
}
