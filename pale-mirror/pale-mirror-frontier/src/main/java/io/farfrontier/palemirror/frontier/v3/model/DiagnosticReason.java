package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Closed producer inventory for current Frontier non-progress and terminal paths.
 * Each entry is a declaration, not a classifier: producers must stamp its complete tuple.
 */
public enum DiagnosticReason {
    RESOURCE_SITE_PLAYER_REMOVED(101, DiagnosticCategory.DOMAIN_DISRUPTION, DiagnosticOwnerKind.RESOURCE_SITE, DiagnosticSubjectKind.RESOURCE_SITE_CELL, DiagnosticDisposition.REPAIR),
    RESOURCE_SITE_EXPLOSION_DAMAGE(102, DiagnosticCategory.DOMAIN_DISRUPTION, DiagnosticOwnerKind.RESOURCE_SITE, DiagnosticSubjectKind.RESOURCE_SITE_CELL, DiagnosticDisposition.REPAIR),
    RESOURCE_SITE_WORKER_DEATH(104, DiagnosticCategory.DOMAIN_DISRUPTION, DiagnosticOwnerKind.RESOURCE_SITE, DiagnosticSubjectKind.HARVEST_WORKER, DiagnosticDisposition.REPAIR),
    RESOURCE_SITE_RECOVERY_UNRESOLVED(105, DiagnosticCategory.RECOVERY_UNKNOWN, DiagnosticOwnerKind.RESOURCE_SITE, DiagnosticSubjectKind.PHYSICAL_EFFECT, DiagnosticDisposition.INSPECT),
    RESOURCE_SITE_CARRIER_FENCE_UNRESOLVED(106, DiagnosticCategory.RECOVERY_UNKNOWN, DiagnosticOwnerKind.RESOURCE_SITE, DiagnosticSubjectKind.SCENE_CARRIER, DiagnosticDisposition.INSPECT),
    RESOURCE_SITE_LIFECYCLE_RECONCILIATION(107, DiagnosticCategory.CANONICAL_INVARIANT_FAILURE, DiagnosticOwnerKind.RESOURCE_SITE, DiagnosticSubjectKind.RESOURCE_SITE_CELL, DiagnosticDisposition.QUARANTINE),
    RESOURCE_SITE_ADAPTER_WRITE_FAILURE(108, DiagnosticCategory.ADAPTER_OR_INFRASTRUCTURE_ERROR, DiagnosticOwnerKind.RESOURCE_SITE, DiagnosticSubjectKind.ADAPTER_BOUNDARY, DiagnosticDisposition.RETRY),
    RESOURCE_SITE_LAWFUL_LAG(109, DiagnosticCategory.WAIT_OR_BLOCKED, DiagnosticOwnerKind.RESOURCE_SITE, DiagnosticSubjectKind.RESOURCE_SITE_CELL, DiagnosticDisposition.RETRY),
    HIVE_GROWTH_BLOCKED(201, DiagnosticCategory.WAIT_OR_BLOCKED, DiagnosticOwnerKind.HIVE_GROWTH, DiagnosticSubjectKind.FACILITY, DiagnosticDisposition.RETRY),
    HIVE_MOBILIZATION_CONFLICT(202, DiagnosticCategory.RECONCILIATION_CONFLICT, DiagnosticOwnerKind.HIVE_MOBILIZATION, DiagnosticSubjectKind.HIVE_COCOON, DiagnosticDisposition.INSPECT),
    HIVE_NUTRIENT_BLOCKED(203, DiagnosticCategory.WAIT_OR_BLOCKED, DiagnosticOwnerKind.HIVE_NUTRIENT_TRANSFER, DiagnosticSubjectKind.HIVE_TRANSFER, DiagnosticDisposition.RETRY),
    ROUTE_PATROL_BLOCKED(301, DiagnosticCategory.WAIT_OR_BLOCKED, DiagnosticOwnerKind.ROUTE_PATROL, DiagnosticSubjectKind.ROUTE_MEMBER, DiagnosticDisposition.RETRY),
    PRODUCTION_BLOCKED(302, DiagnosticCategory.WAIT_OR_BLOCKED, DiagnosticOwnerKind.PRODUCTION_JOB, DiagnosticSubjectKind.PRODUCTION_INPUT, DiagnosticDisposition.RETRY),
    RESIDENT_MIGRATION_BLOCKED(303, DiagnosticCategory.WAIT_OR_BLOCKED, DiagnosticOwnerKind.RESIDENT_MIGRATION, DiagnosticSubjectKind.RESIDENT_ASSIGNMENT, DiagnosticDisposition.RETRY),
    OPERATION_FAILED(304, DiagnosticCategory.RECONCILIATION_CONFLICT, DiagnosticOwnerKind.ROUTE_OPERATION, DiagnosticSubjectKind.ROUTE_OPERATION, DiagnosticDisposition.INSPECT),
    SETTLEMENT_PROVISION_CONFLICT(305, DiagnosticCategory.RECONCILIATION_CONFLICT, DiagnosticOwnerKind.SETTLEMENT_PROVISION, DiagnosticSubjectKind.SETTLEMENT_PROVISION, DiagnosticDisposition.INSPECT),
    SETTLEMENT_ASSAULT_CONFLICT(306, DiagnosticCategory.RECONCILIATION_CONFLICT, DiagnosticOwnerKind.SETTLEMENT_ASSAULT, DiagnosticSubjectKind.SETTLEMENT_ASSAULT, DiagnosticDisposition.INSPECT),
    INVENTORY_CONFLICT(401, DiagnosticCategory.RECONCILIATION_CONFLICT, DiagnosticOwnerKind.INVENTORY_CUSTODY, DiagnosticSubjectKind.INVENTORY_SLOT, DiagnosticDisposition.INSPECT),
    REPLICA_CUSTODY_CONFLICT(402, DiagnosticCategory.RECONCILIATION_CONFLICT, DiagnosticOwnerKind.REPLICA_CUSTODY, DiagnosticSubjectKind.REPLICA, DiagnosticDisposition.INSPECT),
    PHYSICAL_CUSTODY_UNRESOLVED(403, DiagnosticCategory.RECOVERY_UNKNOWN, DiagnosticOwnerKind.PHYSICAL_INTENT, DiagnosticSubjectKind.PHYSICAL_EFFECT, DiagnosticDisposition.INSPECT),
    SCENE_LEASE_RECOVERY_UNRESOLVED(404, DiagnosticCategory.RECOVERY_UNKNOWN, DiagnosticOwnerKind.SCENE_LEASE, DiagnosticSubjectKind.SCENE_CARRIER, DiagnosticDisposition.INSPECT),
    ROUTE_PATROL_MEMBER_LOST(405, DiagnosticCategory.DOMAIN_DISRUPTION, DiagnosticOwnerKind.ROUTE_PATROL, DiagnosticSubjectKind.ROUTE_MEMBER, DiagnosticDisposition.INSPECT),
    FENCED_RECOVERY_AMBIGUOUS(406, DiagnosticCategory.RECOVERY_UNKNOWN, DiagnosticOwnerKind.REPLICA_CUSTODY, DiagnosticSubjectKind.PHYSICAL_EFFECT, DiagnosticDisposition.INSPECT),
    AMBIENT_LEASE_RESTART_ABSENCE(407, DiagnosticCategory.RECOVERY_UNKNOWN, DiagnosticOwnerKind.AMBIENT_LEASE, DiagnosticSubjectKind.AMBIENT_ACTOR, DiagnosticDisposition.INSPECT),
    FRONTIER_QUARANTINE(501, DiagnosticCategory.CANONICAL_INVARIANT_FAILURE, DiagnosticOwnerKind.FRONTIER_INSTANCE, DiagnosticSubjectKind.FRONTIER_INSTANCE, DiagnosticDisposition.QUARANTINE),
    FRONTIER_KERNEL_COMMAND_FAILURE(502, DiagnosticCategory.ADAPTER_OR_INFRASTRUCTURE_ERROR, DiagnosticOwnerKind.FRONTIER_INSTANCE, DiagnosticSubjectKind.FRONTIER_INSTANCE, DiagnosticDisposition.QUARANTINE),
    FRONTIER_KERNEL_TRANSACTION_CAPACITY(503, DiagnosticCategory.CANONICAL_INVARIANT_FAILURE, DiagnosticOwnerKind.FRONTIER_INSTANCE, DiagnosticSubjectKind.FRONTIER_INSTANCE, DiagnosticDisposition.QUARANTINE),
    FRONTIER_KERNEL_DUE_FAILURE(504, DiagnosticCategory.ADAPTER_OR_INFRASTRUCTURE_ERROR, DiagnosticOwnerKind.FRONTIER_INSTANCE, DiagnosticSubjectKind.FRONTIER_INSTANCE, DiagnosticDisposition.QUARANTINE);

    private final int wireTag;
    private final DiagnosticCategory category;
    private final DiagnosticOwnerKind ownerKind;
    private final DiagnosticSubjectKind subjectKind;
    private final DiagnosticDisposition disposition;
    DiagnosticReason(int wireTag, DiagnosticCategory category, DiagnosticOwnerKind ownerKind, DiagnosticSubjectKind subjectKind, DiagnosticDisposition disposition) {
        this.wireTag = wireTag; this.category = category; this.ownerKind = ownerKind; this.subjectKind = subjectKind; this.disposition = disposition;
    }
    public int wireTag() { return wireTag; }
    public DiagnosticCategory category() { return category; }
    public DiagnosticOwnerKind ownerKind() { return ownerKind; }
    public DiagnosticSubjectKind subjectKind() { return subjectKind; }
    public DiagnosticDisposition disposition() { return disposition; }
}
