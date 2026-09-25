package io.farfrontier.palemirror.frontier.v3.api;

/** Immutable, versioned-by-envelope payload carried by one Frontier command or event. */
public interface FrontierPayload {
    /** Stable machine-readable payload kind, for example {@code settlement.population_changed}. */
    String type();

    /** Explicit first-body authorization request; absent on events which do not create actors. */
    default java.util.Optional<ActorBirthIdentity> actorBirth() { return java.util.Optional.empty(); }

    /**
     * Whether accepting this fact creates or changes a physical lease/effect that must be
     * flushed before any external executor may observe it.
     */
    default boolean requiresDurableBeforeEffect() {
        return false;
    }
}
