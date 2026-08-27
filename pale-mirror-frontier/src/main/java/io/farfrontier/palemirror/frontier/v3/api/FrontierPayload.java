package io.farfrontier.palemirror.frontier.v3.api;

/** Immutable, versioned-by-envelope payload carried by one Frontier command or event. */
public interface FrontierPayload {
    /** Stable machine-readable payload kind, for example {@code settlement.population_changed}. */
    String type();
}
