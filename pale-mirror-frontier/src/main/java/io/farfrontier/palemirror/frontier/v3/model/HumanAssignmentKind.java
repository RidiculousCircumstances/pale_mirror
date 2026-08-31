package io.farfrontier.palemirror.frontier.v3.model;

/** Current work derived from the exact durable task or operation that owns it. */
public enum HumanAssignmentKind {
    IDLE,
    INDUSTRIAL_WORK,
    FIELD_HARVEST,
    CARGO_TRANSPORT,
    ESCORT,
    ROUTE_PATROL,
    SETTLEMENT_DEFENCE,
    TRANSIT
}
