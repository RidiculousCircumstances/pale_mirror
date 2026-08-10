package io.farfrontier.palemirror.api;

/** Semantic adapter capabilities; no third-party registry names leak into the domain. */
public enum Capability {
    PM_ANCHOR_MATERIALIZATION,
    PM_ANCHOR_OBSERVATION,
    /** Optional source-native encounter presentation. */
    SOURCE_ENCOUNTER_ACTORS,
    /** Optional source-owned physical representation for a PM gate. */
    SOURCE_GATE,
    /** PM-owned health, damage and attack effects for source carriers. */
    SOURCE_CONTROLLED_COMBAT,
    /** PM-owned local movement for constrained source carriers. */
    SOURCE_CONTROLLED_MOVEMENT,
    /** Source integration suppresses or contains its autonomous global mechanics. */
    SOURCE_GLOBAL_ISOLATION,
    /** Read-only observation of a player-built logistics route. */
    LOGISTICS_ROUTE_OBSERVATION,
    /** Read-only discovery of an already generated vanilla-compatible settlement. */
    SETTLEMENT_OBSERVATION,
    /** Optional player-facing mirror of PM scenario state in FTB Quests. */
    FTB_QUEST_PRESENTATION
}
