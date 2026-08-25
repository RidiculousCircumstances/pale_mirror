package io.farfrontier.palemirror.frontier.reference;

/** Typed result of Python {@code Settlement.resolve_swarm_attack}. */
public record ReferenceSwarmAttackResolution(
        boolean destroyed,
        double damage,
        double defence,
        double structuralBreach,
        double populationLoss
) { }
