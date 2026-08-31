package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Independently bounded kinds of durable owner work.
 *
 * <p>One settlement may pursue one world-facing strategic objective while one
 * named facility performs local work.  The lane is derived from the objective
 * kind, not stored independently, so snapshots and WAL retain one source of
 * truth and old objective payloads remain unambiguous.</p>
 */
public enum StrategicObjectiveLane {
    STRATEGIC,
    FACILITY;

    static StrategicObjectiveLane forKind(StrategicObjectiveKind kind) {
        return switch (kind) {
            case SETTLEMENT_HARVEST_RESOURCE_SITE -> FACILITY;
            default -> STRATEGIC;
        };
    }
}
