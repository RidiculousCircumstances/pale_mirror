package io.farfrontier.palemirror.internal.observation;

/** Typed fact produced by Minecraft or an adapter. Observations never mutate the domain directly. */
public sealed interface Observation permits PlayerEnteredFacilityBounds, ThreatControllerDestroyed,
        MaterializationPostconditionObserved, EncounterActorDestroyed, GatePartDestroyed {
    String id();
}
