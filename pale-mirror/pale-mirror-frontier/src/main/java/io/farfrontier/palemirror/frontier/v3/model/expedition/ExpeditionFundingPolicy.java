package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Objects;
import java.util.Optional;

/** Required itinerary purchases are a gate; optional contingency is not. No money is created. */
public final class ExpeditionFundingPolicy {
    private ExpeditionFundingPolicy() { }

    public static Optional<FixedScalar> allocation(FixedScalar requiredPurchases,
            FixedScalar contingencyCeiling, FixedScalar available) {
        Objects.requireNonNull(requiredPurchases); Objects.requireNonNull(contingencyCeiling);
        Objects.requireNonNull(available);
        if (requiredPurchases.raw() < 0 || contingencyCeiling.raw() < 0 || available.raw() < 0)
            throw new IllegalArgumentException("expedition funding amounts cannot be negative");
        if (available.compareTo(requiredPurchases) < 0) return Optional.empty();
        long contingency = Math.min(contingencyCeiling.raw(), available.minus(requiredPurchases).raw());
        return Optional.of(requiredPurchases.plus(new FixedScalar(contingency)));
    }
}
