package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Objects;

/** Ruleset-owned capacity and planning policy, not a second inventory or clock. */
public record ExpeditionRules(int personalStackSlots, int personalFoodItems,
                              int packAnimalStackSlots, long ticksPerRouteEdge,
                              long destinationWorkTicks, int durationMarginPermille,
                              FixedScalar replenishmentBudget, int formationSpacing,
                              int maxFormationStretch) {
    public ExpeditionRules {
        Objects.requireNonNull(replenishmentBudget, "expedition replenishment budget");
        if (personalStackSlots < 1 || personalStackSlots > 10 || personalFoodItems < 1
                || personalFoodItems > 64 || packAnimalStackSlots < 1 || packAnimalStackSlots > 54
                || ticksPerRouteEdge < 1 || destinationWorkTicks < 0
                || durationMarginPermille < 0 || durationMarginPermille > 10_000
                || replenishmentBudget.raw() < 0 || formationSpacing < 2 || formationSpacing > 16
                || maxFormationStretch < formationSpacing || maxFormationStretch > 128)
            throw new IllegalArgumentException("invalid expedition policy");
    }

    public static ExpeditionRules initial() {
        return new ExpeditionRules(10, 8, 15, 20L, 400L, 250,
                FixedScalar.whole(20), 2, 12);
    }

    public long plannedDuration(long outboundEdges, long returnEdges) {
        if (outboundEdges < 0 || returnEdges < 0)
            throw new IllegalArgumentException("negative expedition route length");
        long duration = Math.addExact(Math.multiplyExact(Math.addExact(outboundEdges, returnEdges),
                ticksPerRouteEdge), destinationWorkTicks);
        return Math.addExact(duration, Math.ceilDiv(Math.multiplyExact(duration,
                (long) durationMarginPermille), 1_000L));
    }

    public String canonicalText() {
        return personalStackSlots + ":" + personalFoodItems + ":" + packAnimalStackSlots + ":"
                + ticksPerRouteEdge + ":" + destinationWorkTicks + ":" + durationMarginPermille
                + ":" + replenishmentBudget.raw() + ":" + formationSpacing + ":" + maxFormationStretch;
    }
}
