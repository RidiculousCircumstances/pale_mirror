package io.farfrontier.palemirror.frontier.reference;

/** Reporting-only result of one source swarm arrival at a settlement. */
public record ReferenceCombatReceipt(
        int day,
        int swarmId,
        int settlementId,
        int x,
        int y,
        double power,
        String composition,
        ReferenceFormationPhase phase,
        double defence,
        double support,
        double structuralBreach,
        double damage,
        boolean destroyed
) { }
