package io.farfrontier.palemirror.frontier.v3.model;

/** Stable non-ordinal wire identities. Decoding is direct and never discovers a compatible tuple. */
public final class DiagnosticWireTags {
    private DiagnosticWireTags() { }
    public static DiagnosticReason reason(int tag) { return switch (tag) {
        case 101 -> DiagnosticReason.RESOURCE_SITE_PLAYER_REMOVED; case 102 -> DiagnosticReason.RESOURCE_SITE_EXPLOSION_DAMAGE;
        case 104 -> DiagnosticReason.RESOURCE_SITE_WORKER_DEATH;
        case 105 -> DiagnosticReason.RESOURCE_SITE_RECOVERY_UNRESOLVED; case 106 -> DiagnosticReason.RESOURCE_SITE_CARRIER_FENCE_UNRESOLVED;
        case 107 -> DiagnosticReason.RESOURCE_SITE_LIFECYCLE_RECONCILIATION; case 108 -> DiagnosticReason.RESOURCE_SITE_ADAPTER_WRITE_FAILURE;
        case 109 -> DiagnosticReason.RESOURCE_SITE_LAWFUL_LAG; case 201 -> DiagnosticReason.HIVE_GROWTH_BLOCKED;
        case 202 -> DiagnosticReason.HIVE_MOBILIZATION_CONFLICT; case 203 -> DiagnosticReason.HIVE_NUTRIENT_BLOCKED;
        case 301 -> DiagnosticReason.ROUTE_PATROL_BLOCKED; case 302 -> DiagnosticReason.PRODUCTION_BLOCKED;
        case 303 -> DiagnosticReason.RESIDENT_MIGRATION_BLOCKED; case 304 -> DiagnosticReason.OPERATION_FAILED;
        case 305 -> DiagnosticReason.SETTLEMENT_PROVISION_CONFLICT; case 306 -> DiagnosticReason.SETTLEMENT_ASSAULT_CONFLICT; case 401 -> DiagnosticReason.INVENTORY_CONFLICT;
        case 402 -> DiagnosticReason.REPLICA_CUSTODY_CONFLICT; case 403 -> DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED;
        case 501 -> DiagnosticReason.FRONTIER_QUARANTINE; case 502 -> DiagnosticReason.FRONTIER_KERNEL_COMMAND_FAILURE;
        case 503 -> DiagnosticReason.FRONTIER_KERNEL_TRANSACTION_CAPACITY; case 504 -> DiagnosticReason.FRONTIER_KERNEL_DUE_FAILURE;
        default -> throw new IllegalArgumentException("unknown diagnostic reason tag: " + tag); }; }
    public static DiagnosticCategory category(int tag) { return switch (tag) {
        case 1 -> DiagnosticCategory.WAIT_OR_BLOCKED; case 2 -> DiagnosticCategory.DOMAIN_DISRUPTION;
        case 3 -> DiagnosticCategory.RECONCILIATION_CONFLICT; case 4 -> DiagnosticCategory.RECOVERY_UNKNOWN;
        case 5 -> DiagnosticCategory.CANONICAL_INVARIANT_FAILURE; case 6 -> DiagnosticCategory.ADAPTER_OR_INFRASTRUCTURE_ERROR;
        default -> throw new IllegalArgumentException("unknown diagnostic category tag: " + tag); }; }
    public static DiagnosticDisposition disposition(int tag) { return switch (tag) {
        case 1 -> DiagnosticDisposition.RETRY; case 2 -> DiagnosticDisposition.REPAIR; case 3 -> DiagnosticDisposition.INSPECT;
        case 4 -> DiagnosticDisposition.ABANDON; case 5 -> DiagnosticDisposition.QUARANTINE; case 6 -> DiagnosticDisposition.REJECT_STALE;
        default -> throw new IllegalArgumentException("unknown diagnostic disposition tag: " + tag); }; }
    public static DiagnosticOwnerKind ownerKind(int tag) { return switch (tag) {
        case 1 -> DiagnosticOwnerKind.RESOURCE_SITE; case 2 -> DiagnosticOwnerKind.HARVEST_JOB; case 3 -> DiagnosticOwnerKind.PHYSICAL_INTENT;
        case 4 -> DiagnosticOwnerKind.SCENE_LEASE; case 5 -> DiagnosticOwnerKind.HIVE_MOBILIZATION; case 6 -> DiagnosticOwnerKind.ROUTE_PATROL;
        case 7 -> DiagnosticOwnerKind.PRODUCTION_JOB; case 8 -> DiagnosticOwnerKind.SETTLEMENT_SERVICE_WORK; case 9 -> DiagnosticOwnerKind.ROUTE_MAINTENANCE;
        case 10 -> DiagnosticOwnerKind.ROUTE_CONSTRUCTION; case 11 -> DiagnosticOwnerKind.MEDICAL_EVACUATION; case 12 -> DiagnosticOwnerKind.INVENTORY_CUSTODY;
        case 13 -> DiagnosticOwnerKind.REPLICA_CUSTODY; case 14 -> DiagnosticOwnerKind.FRONTIER_INSTANCE; case 15 -> DiagnosticOwnerKind.ADAPTER;
        case 16 -> DiagnosticOwnerKind.HIVE_GROWTH; case 17 -> DiagnosticOwnerKind.HIVE_NUTRIENT_TRANSFER; case 18 -> DiagnosticOwnerKind.RESIDENT_MIGRATION;
        case 19 -> DiagnosticOwnerKind.ROUTE_OPERATION; case 20 -> DiagnosticOwnerKind.SETTLEMENT_PROVISION; case 21 -> DiagnosticOwnerKind.SETTLEMENT_ASSAULT;
        default -> throw new IllegalArgumentException("unknown diagnostic owner kind tag: " + tag); }; }
    public static DiagnosticSubjectKind subjectKind(int tag) { return switch (tag) {
        case 1 -> DiagnosticSubjectKind.RESOURCE_SITE_CELL; case 2 -> DiagnosticSubjectKind.HARVEST_WORKER; case 3 -> DiagnosticSubjectKind.PHYSICAL_EFFECT;
        case 4 -> DiagnosticSubjectKind.SCENE_CARRIER; case 5 -> DiagnosticSubjectKind.HIVE_COCOON; case 6 -> DiagnosticSubjectKind.HIVE_TRANSFER;
        case 7 -> DiagnosticSubjectKind.ROUTE_MEMBER; case 8 -> DiagnosticSubjectKind.ROUTE_CELL; case 9 -> DiagnosticSubjectKind.PRODUCTION_INPUT;
        case 10 -> DiagnosticSubjectKind.FACILITY; case 11 -> DiagnosticSubjectKind.RESIDENT_ASSIGNMENT; case 12 -> DiagnosticSubjectKind.MEDICAL_PATIENT;
        case 13 -> DiagnosticSubjectKind.INVENTORY_SLOT; case 14 -> DiagnosticSubjectKind.REPLICA; case 15 -> DiagnosticSubjectKind.FRONTIER_INSTANCE;
        case 16 -> DiagnosticSubjectKind.ADAPTER_BOUNDARY; case 17 -> DiagnosticSubjectKind.ROUTE_OPERATION; case 18 -> DiagnosticSubjectKind.SETTLEMENT_PROVISION;
        case 19 -> DiagnosticSubjectKind.SETTLEMENT_ASSAULT; default -> throw new IllegalArgumentException("unknown diagnostic subject kind tag: " + tag); }; }
    public static int ownerTag(DiagnosticOwnerKind kind) { return switch (kind) {
        case RESOURCE_SITE -> 1; case HARVEST_JOB -> 2; case PHYSICAL_INTENT -> 3; case SCENE_LEASE -> 4; case HIVE_MOBILIZATION -> 5;
        case ROUTE_PATROL -> 6; case PRODUCTION_JOB -> 7; case SETTLEMENT_SERVICE_WORK -> 8; case ROUTE_MAINTENANCE -> 9;
        case ROUTE_CONSTRUCTION -> 10; case MEDICAL_EVACUATION -> 11; case INVENTORY_CUSTODY -> 12; case REPLICA_CUSTODY -> 13;
        case FRONTIER_INSTANCE -> 14; case ADAPTER -> 15; case HIVE_GROWTH -> 16; case HIVE_NUTRIENT_TRANSFER -> 17; case RESIDENT_MIGRATION -> 18;
        case ROUTE_OPERATION -> 19; case SETTLEMENT_PROVISION -> 20; case SETTLEMENT_ASSAULT -> 21; }; }
    public static int subjectTag(DiagnosticSubjectKind kind) { return switch (kind) {
        case RESOURCE_SITE_CELL -> 1; case HARVEST_WORKER -> 2; case PHYSICAL_EFFECT -> 3; case SCENE_CARRIER -> 4;
        case HIVE_COCOON -> 5; case HIVE_TRANSFER -> 6; case ROUTE_MEMBER -> 7; case ROUTE_CELL -> 8;
        case PRODUCTION_INPUT -> 9; case FACILITY -> 10; case RESIDENT_ASSIGNMENT -> 11; case MEDICAL_PATIENT -> 12;
        case INVENTORY_SLOT -> 13; case REPLICA -> 14; case FRONTIER_INSTANCE -> 15; case ADAPTER_BOUNDARY -> 16;
        case ROUTE_OPERATION -> 17; case SETTLEMENT_PROVISION -> 18; case SETTLEMENT_ASSAULT -> 19; }; }
}
