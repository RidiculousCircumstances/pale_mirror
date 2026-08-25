package io.farfrontier.palemirror.frontier.reference;

/** Exact immutable source rule for a resource's target and scarcity valuation. */
public record ReferenceResourceRule(
        double referenceValue,
        double reserveDays,
        double scarcityElasticity,
        double minimumTarget
) { }
