package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Owner-local replacement before effect preparation; no receipt, title or carried stock is forgotten. */
public record ShipmentStorageRetargeted(SubjectId shipmentId, long expectedRevision, ShipmentEndpoint destination)
        implements FrontierPayload {
    public ShipmentStorageRetargeted { Objects.requireNonNull(shipmentId); Objects.requireNonNull(destination); if (expectedRevision < 1) throw new IllegalArgumentException("stale storage retarget"); }
    @Override public String type() { return "frontier.shipment_storage_retargeted"; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId owner) {
        var current = state.shipments().shipments().get(shipmentId);
        if (current == null || !owner.equals(shipmentId) || current.revision() != expectedRevision
                || state.actorMovements().containsKey(current.execution().actorId()) || !unbegun(current))
            throw new IllegalArgumentException("storage retarget lost its exact safe shipment boundary");
        if (retainedAvailable(state, current)) throw new IllegalArgumentException("working storage cannot be silently retargeted");
        var candidate = eligible(state, current).filter(destination::equals).orElseThrow(
                () -> new IllegalArgumentException("replacement is not the current eligible storage selection"));
        var account = FungibleResourceCustodySupport.accountAtContainer(state, candidate.containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(candidate.containerId()));
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replace(current, current.retargetStorage(candidate, account))));
    }
    public static boolean unbegun(Shipment shipment) {
        return !shipment.terminal() && shipment.authorization().kind() == ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT
                && shipment.pendingPhysicalStep().isEmpty() && shipment.reception().isEmpty();
    }
    public static boolean retainedAvailable(FrontierWorldState state, Shipment shipment) {
        var container = state.inventory().containers().get(shipment.receiver().containerId());
        return container != null && container.storage().map(policy -> policy.endpoint().equals(shipment.receiver())
                && policy.filter().accepts(shipment.itemKind())).orElse(false)
                && !ReferenceContainerCustody.blocksCanonicalUse(state, container.id())
                && ContainerStorageAdmission.receive(state, container.id(), shipment.itemKind(), shipment.quantity(), Optional.of(shipment.id()));
    }
    public static Optional<ShipmentEndpoint> eligible(FrontierWorldState state, Shipment shipment) {
        if (!unbegun(shipment)) return Optional.empty();
        var actor = shipment.execution().actorId(); var origin = state.actorLocations().get(actor).supportingSurface();
        return ResourceStorageSelection.destination(state, shipment.receiver().settlementId(), shipment.itemKind(), shipment.quantity(),
                origin, Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> {
                    if (endpoint.containerId().equals(shipment.receiver().containerId()) || shipment.sender().container().filter(endpoint.containerId()::equals).isPresent()) return false;
                    var order = new MovementOrder(shipment.id(), actor, 1, shipment.revision(), List.of(endpoint.station()),
                            TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
                    try { ResourceSourceComposition.knowledge(state, shipment.sender(), endpoint).plannedPath(origin, order); return true; }
                    catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return false; }
                });
    }
}
