package io.farfrontier.palemirror.frontier.v3.model;

/** Stable ordinal parsing for deterministic named schedule identities. */
final class FrontierWorldScheduleSupport {
    private FrontierWorldScheduleSupport() { }

    static int ordinal(String id) {
        int separator = id.lastIndexOf('-');
        if (separator < 0 || separator == id.length() - 1) throw new IllegalArgumentException("scheduled work identity lacks ordinal: " + id);
        try {
            int value = Integer.parseInt(id.substring(separator + 1));
            if (value <= 0) throw new IllegalArgumentException("scheduled work ordinal must be positive: " + id);
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("scheduled work identity has malformed ordinal: " + id, error);
        }
    }
}
