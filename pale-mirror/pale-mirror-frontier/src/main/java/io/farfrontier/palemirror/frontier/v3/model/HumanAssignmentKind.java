package io.farfrontier.palemirror.frontier.v3.model;

/** Current work derived from the exact durable task or operation that owns it. */
public enum HumanAssignmentKind {
    IDLE,
    PRODUCTION,
    FIELD_HARVEST,
    CARGO_TRANSPORT,
    COURIER,
    GROUP_MEMBER,
    ESCORT,
    ROUTE_PATROL,
    SETTLEMENT_DEFENCE,
    ENGINEERING_RECOVERY,
    SETTLEMENT_SERVICE,
    MEDICAL_EVACUATION,
    TRANSIT
}
