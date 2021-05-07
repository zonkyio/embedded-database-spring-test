package io.zonky.test.db.util;

import java.util.concurrent.ThreadLocalRandom;

public class RandomStringUtils {

    private RandomStringUtils() {}

    public static String randomAlphabetic(int length) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return random.ints(leftLimit, rightLimit + 1)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
