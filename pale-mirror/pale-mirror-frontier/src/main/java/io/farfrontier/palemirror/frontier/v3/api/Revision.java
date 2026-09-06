package io.farfrontier.palemirror.frontier.v3.api;

/** Monotonic canonical state revision. */
public record Revision(long value) implements Comparable<Revision> {
    public static final Revision ZERO = new Revision(0L);

    public Revision {
        if (value < 0L) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }

    public Revision next() {
        return new Revision(Math.addExact(value, 1L));
    }

    @Override
    public int compareTo(Revision other) {
        return Long.compare(value, other.value);
    }
}
