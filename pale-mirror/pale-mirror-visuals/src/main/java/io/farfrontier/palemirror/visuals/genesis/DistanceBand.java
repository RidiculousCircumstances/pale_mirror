package io.farfrontier.palemirror.visuals.genesis;

/** Inclusive horizontal distance contract used by reusable regional placement profiles. */
public record DistanceBand(int minimum, int maximum) {
    public DistanceBand {
        if (minimum < 0) throw new IllegalArgumentException("minimum distance must be non-negative");
        if (maximum < minimum) throw new IllegalArgumentException("maximum distance must not be below minimum");
    }

    public int span() { return maximum - minimum + 1; }

    public boolean containsSquared(long squaredDistance) {
        return squaredDistance >= (long) minimum * minimum && squaredDistance <= (long) maximum * maximum;
    }
}
