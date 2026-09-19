package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Optional;

/** Closed payload inventory; extraction only exposes the producer's already validated tuple. */
public final class DiagnosticIncidentExtractor {
    private DiagnosticIncidentExtractor() { }
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
