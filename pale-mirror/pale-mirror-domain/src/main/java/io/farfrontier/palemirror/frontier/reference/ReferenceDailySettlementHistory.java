package io.farfrontier.palemirror.frontier.reference;

import java.util.Map;
import java.util.Objects;

/** Immutable diagnostic row matching one source settlement-history day. */
public record ReferenceDailySettlementHistory(
        int day,
        boolean alive,
        double population,
        double cash,
        double integrity,
        double threat,
        double illnessBurden,
        double medicineFulfillment,
        double woundedPersonnel,
        Map<ReferenceResource, ReferenceResourceHistory> resources
) {
    public ReferenceDailySettlementHistory {
        resources = Map.copyOf(Objects.requireNonNull(resources, "resources"));
    }

    /** Per-resource fields retained by Python's settlement dashboard history. */
    public record ReferenceResourceHistory(
            double amount,
            double target,
            double value,
            double production,
            double consumption
    ) { }
}
