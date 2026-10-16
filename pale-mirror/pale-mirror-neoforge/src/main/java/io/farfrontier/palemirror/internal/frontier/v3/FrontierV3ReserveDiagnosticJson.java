package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.Map;
import java.util.stream.Collectors;

/** Bounded read-only explanation of buffer advice, not a delivery guarantee. */
final class FrontierV3ReserveDiagnosticJson {
    static String render(SupplyPlanner.Plan plan) {
        return plan.reserves().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> {
            var value = e.getValue();
            return "{\"commodity\":\"" + FrontierV3DiagnosticJson.quote(e.getKey()) + "\",\"target\":" + value.quantity()
                    + ",\"reorderPoint\":" + value.reorderPoint() + ",\"protectedMinimum\":" + value.protectedQuantity()
                    + ",\"coverageTicks\":" + value.horizonTicks() + ",\"replenishmentTicks\":" + value.timing().leadTicks()
                    + ",\"timingBasis\":\"" + value.timing().basis().name() + "\",\"consumptionNumerator\":" + value.consumption().numerator()
                    + ",\"consumptionDenominator\":" + value.consumption().denominator() + "}";
        }).collect(Collectors.joining(",", "[", "]"));
    }
    private FrontierV3ReserveDiagnosticJson() { }
}
