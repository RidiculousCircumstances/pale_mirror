package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Current operational quality of one exact settlement defence unit.
 *
 * <p>This is derived from the retained unit, the current exclusive assignments,
 * exact actor-held equipment and actor vitality.  It is deliberately not another
 * persisted state machine.</p>
 */
public enum SettlementDefenderReadinessStatus {
    UNAVAILABLE,
    IMPROVISED,
    DEGRADED,
    READY
}
