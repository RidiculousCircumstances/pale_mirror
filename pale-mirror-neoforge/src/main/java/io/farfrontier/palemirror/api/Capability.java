package io.farfrontier.palemirror.api;

/** Semantic adapter capabilities; no third-party registry names leak into the domain. */
public enum Capability {
    PM_ANCHOR_MATERIALIZATION,
    PM_ANCHOR_OBSERVATION,
    /** Optional presentation capability. It is never required for core recovery. */
    CRIMSON_ENCOUNTER_ACTORS,
    /** Optional PM-owned Nodes, Bloodlinks and boss forms from the pinned Crimson sandbox. */
    CRIMSON_SIEGE_OBJECTS,
    /** Optional constrained native forms from the version-pinned Spore sandbox. */
    SPORE_ENCOUNTER_ACTORS,
    /** PM-owned combat health and attacks for constrained Spore forms. */
    SPORE_CONTROLLED_COMBAT,
    /** PM-owned patrol and pursuit while Spore native AI stays disabled. */
    SPORE_CONTROLLED_MOVEMENT,
    /** Version-pinned suppression of Spore global spawning and infection handlers. */
    SPORE_GLOBAL_ISOLATION
}
