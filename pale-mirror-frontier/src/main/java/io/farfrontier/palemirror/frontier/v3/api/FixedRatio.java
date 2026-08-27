package io.farfrontier.palemirror.frontier.v3.api;

/** Inclusive [0, 1] fixed-point ratio. */
public record FixedRatio(FixedScalar value) {
    public FixedRatio {
        if (value.compareTo(FixedScalar.ZERO) < 0 || value.compareTo(FixedScalar.ONE) > 0) {
            throw new IllegalArgumentException("ratio must be between zero and one");
        }
    }
}
