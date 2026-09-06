package io.farfrontier.palemirror.visuals.genesis;

/** Bounded authored-region cardinality: minimum is mandatory, target is desired, maximum is opportunistic. */
public record RegionCountRange(int minimum, int target, int maximum) {
    public RegionCountRange {
        if (minimum < 1 || target < minimum || maximum < target) {
            throw new IllegalArgumentException("region counts must satisfy 1 <= minimum <= target <= maximum");
        }
    }

    public static RegionCountRange exact(int count) {
        return new RegionCountRange(count, count, count);
    }
}
