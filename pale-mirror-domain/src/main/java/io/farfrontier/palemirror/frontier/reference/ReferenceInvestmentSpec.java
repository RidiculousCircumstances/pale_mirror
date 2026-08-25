package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** One real-material facility investment from Python {@code InvestmentSpec}. */
public record ReferenceInvestmentSpec(
        String capacityAttribute,
        ReferenceResource output,
        double timber,
        double ore,
        double tools,
        double baseGain
) {
    public ReferenceInvestmentSpec {
        Objects.requireNonNull(capacityAttribute, "capacityAttribute");
    }
}
