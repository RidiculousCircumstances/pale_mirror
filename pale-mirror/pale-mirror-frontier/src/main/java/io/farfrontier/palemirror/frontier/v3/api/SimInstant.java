package io.farfrontier.palemirror.frontier.v3.api;

/** Monotonic simulation time measured in running server-tick units. */
public record SimInstant(long ticks) implements Comparable<SimInstant> {
    public static final SimInstant ZERO = new SimInstant(0L);

    public SimInstant {
        if (ticks < 0L) {
            throw new IllegalArgumentException("simulation time cannot be negative");
        }
    }

    public SimInstant plus(long elapsedTicks) {
        if (elapsedTicks < 0L) {
            throw new IllegalArgumentException("elapsed ticks cannot be negative");
        }
        return new SimInstant(Math.addExact(ticks, elapsedTicks));
    }

    @Override
    public int compareTo(SimInstant other) {
        return Long.compare(ticks, other.ticks);
    }
}
