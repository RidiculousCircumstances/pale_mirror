package io.farfrontier.palemirror.frontier.v3.model;

/** Latest exact food-security result for one settlement; it is not an aggregate stock counter. */
public enum SettlementProvisionStatus {
    IDLE,
    IN_PROGRESS,
    SECURE,
    RATIONED,
    SHORTAGE,
    CONFLICT
}
