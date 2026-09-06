package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;
import java.util.regex.Pattern;

final class Identifier {
    private static final Pattern VALUE = Pattern.compile("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}");

    private Identifier() {
    }

    static String require(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be namespace:path: " + value);
        }
        return value;
    }
}
