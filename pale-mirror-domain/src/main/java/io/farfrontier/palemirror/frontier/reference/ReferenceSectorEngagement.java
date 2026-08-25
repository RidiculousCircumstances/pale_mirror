package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Bounded diagnostic receipt for a resolved frontier action. */
public record ReferenceSectorEngagement(
        int day,
        String sectorKey,
        String kind,
        String attacker,
        String defender,
        double power,
        double cordonChange,
        double infectionChange,
        double personnelLoss,
        String outcome
) {
    public ReferenceSectorEngagement {
        sectorKey = Objects.requireNonNull(sectorKey, "sectorKey");
        kind = Objects.requireNonNull(kind, "kind");
        attacker = Objects.requireNonNull(attacker, "attacker");
        defender = Objects.requireNonNull(defender, "defender");
        outcome = Objects.requireNonNull(outcome, "outcome");
    }
}
