package io.farfrontier.palemirror.api;

/** Semantic adapter capabilities; no third-party registry names leak into the domain. */
public enum Capability {
    PM_ANCHOR_MATERIALIZATION,
    PM_ANCHOR_OBSERVATION,
    /** Optional presentation capability. It is never required for core recovery. */
    CRIMSON_ENCOUNTER_ACTORS
}
