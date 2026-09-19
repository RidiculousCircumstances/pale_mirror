package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Closed payload inventory; extraction only exposes the producer's already validated tuple. */
public final class DiagnosticIncidentExtractor {
    private DiagnosticIncidentExtractor() { }
    /** Closed mechanically-auditable Frontier event inventory; legacy quarantine owns its own SavedData entry. */
    public static Set<DiagnosticReason> retainedReasons() {
        EnumSet<DiagnosticReason> values = EnumSet.noneOf(DiagnosticReason.class);
        for (ResourceSiteDiagnosticProducer producer : ResourceSiteDiagnosticProducer.values()) values.add(producer.diagnosticReason());
        values.addAll(Set.of(DiagnosticReason.HIVE_GROWTH_BLOCKED, DiagnosticReason.HIVE_MOBILIZATION_CONFLICT,
                DiagnosticReason.HIVE_NUTRIENT_BLOCKED, DiagnosticReason.ROUTE_PATROL_BLOCKED, DiagnosticReason.PRODUCTION_BLOCKED,
                DiagnosticReason.RESIDENT_MIGRATION_BLOCKED, DiagnosticReason.OPERATION_FAILED, DiagnosticReason.SETTLEMENT_PROVISION_CONFLICT,
                DiagnosticReason.SETTLEMENT_ASSAULT_CONFLICT, DiagnosticReason.INVENTORY_CONFLICT, DiagnosticReason.REPLICA_CUSTODY_CONFLICT,
                DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED, DiagnosticReason.FRONTIER_KERNEL_COMMAND_FAILURE,
                DiagnosticReason.FRONTIER_KERNEL_TRANSACTION_CAPACITY, DiagnosticReason.FRONTIER_KERNEL_DUE_FAILURE));
        return Set.copyOf(values);
    }
    public static Optional<DiagnosticTuple> tuple(FrontierPayload payload) {
        return switch (payload) {
            case ResourceSiteConflictObserved value -> Optional.of(value.diagnostic());
            case HiveGrowthBlocked value -> Optional.of(value.diagnostic());
            case HiveMobilizationConflicted value -> Optional.of(value.diagnostic());
            case HiveNutrientTransferBlocked value -> Optional.of(value.diagnostic());
            case RoutePatrolBlocked value -> Optional.of(value.diagnostic());
            case ProductionBlocked value -> Optional.of(value.diagnostic());
            case ResidentMigrationBlocked value -> Optional.of(value.diagnostic());
            case OperationFailed value -> Optional.of(value.diagnostic());
            case SettlementProvisionResolved value -> value.diagnostic();
            case SettlementAssaultTransition value -> value.diagnostic();
            case InventoryConflictObserved value -> Optional.of(value.conflict().diagnostic());
            case PhysicalReplicaCustodyPayloads.ReplicaConflictObserved value -> Optional.of(value.diagnostic());
            case PhysicalReplicaCustodyPayloads.CustodyUnresolved value -> Optional.of(value.diagnostic());
            case PhysicalIntentTransition value -> value.diagnostic();
            case KernelQuarantineObserved value -> Optional.of(value.diagnostic());
            default -> Optional.empty();
        };
    }
    /** Preserve an owner-retained incident link where that aggregate already has one. */
    public static String incidentId(FrontierPayload payload, FrontierWorldState reduced, DiagnosticTuple tuple) {
        if (payload instanceof ResourceSiteConflictObserved value) {
            ResourceSiteLifecycle lifecycle = reduced.resourceSites().sites().get(value.siteId());
            if (lifecycle == null || lifecycle.conflictDisposition().isEmpty()) throw new IllegalStateException("resource-site incident link was not reduced");
            return lifecycle.conflictDisposition().orElseThrow().incident().id();
        }
        return DiagnosticIncident.idFor(tuple);
    }
}
