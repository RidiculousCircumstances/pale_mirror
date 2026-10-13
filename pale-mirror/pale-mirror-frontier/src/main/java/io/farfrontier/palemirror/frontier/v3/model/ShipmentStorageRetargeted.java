package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.*;

/** Receiver-approved substitution before effect preparation; no receipt, title or carried stock is forgotten. */
public record ShipmentStorageRetargeted(SubjectId shipmentId, long expectedRevision,
        ActorExecutionId execution, SurfaceAnchor origin, ShipmentEndpoint destination,
        Optional<PedestrianRouteReceipt> route)
        implements FrontierPayload {
    public ShipmentStorageRetargeted {
        Objects.requireNonNull(shipmentId); Objects.requireNonNull(execution); Objects.requireNonNull(origin);
        Objects.requireNonNull(destination); Objects.requireNonNull(route);
        if (expectedRevision < 1 || !execution.activityOwnerId().equals(shipmentId) || execution.activityKind() != ActorActivityKind.COURIER)
            throw new IllegalArgumentException("storage retarget needs its exact courier execution");
        var accepted = route.map(PedestrianRouteReceipt::route).orElseGet(() -> List.of(origin));
        if (!accepted.getFirst().equals(origin) || !accepted.getLast().equals(destination.station()))
            throw new IllegalArgumentException("storage retarget has foreign route endpoints");
    }
    @Override public String type() { return "frontier.shipment_storage_retargeted"; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId owner) {
        var current = state.shipments().shipments().get(shipmentId);
        if (current == null || !owner.equals(shipmentId) || current.revision() != expectedRevision
                || !current.execution().equals(execution) || !state.actorLocations().get(execution.actorId()).supportingSurface().equals(origin)
                || state.actorMovements().containsKey(current.execution().actorId()) || !unbegun(current))
            throw new IllegalArgumentException("storage retarget lost its exact safe shipment boundary");
        if (retainedAvailable(state, current)) throw new IllegalArgumentException("working storage cannot be silently retargeted");
        state.actorExecutions().requireCurrent(execution);
        if (destination.containerId().equals(current.receiver().containerId())
                || current.sender().container().filter(destination.containerId()::equals).isPresent()
                || !ShipmentEndpointComposition.replacementPolicy(current.receiver()).admits(state, current, destination))
            throw new IllegalArgumentException("replacement storage has stale capacity, filter or owner");
        ResourceSourceComposition.knowledge(state, current.sender(), destination).requireRoute(
                route.map(PedestrianRouteReceipt::route).orElseGet(() -> List.of(origin)));
        var account = FungibleResourceCustodySupport.accountAtContainer(state, destination.containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(destination.containerId()));
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replace(current, current.retargetStorage(destination, account))));
    }
    public static boolean unbegun(Shipment shipment) {
        return !shipment.terminal() && shipment.authorization().kind() == ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT
                && shipment.pendingPhysicalStep().isEmpty() && shipment.reception().isEmpty();
    }
    public static boolean retainedAvailable(FrontierWorldState state, Shipment shipment) {
        return ShipmentEndpointComposition.replacementPolicy(shipment.receiver()).retainedAvailable(state, shipment);
    }
    public static Optional<ShipmentStorageRetargeted> eligible(FrontierWorldState state, Shipment shipment) {
        if (!unbegun(shipment)) return Optional.empty();
        var actor = shipment.execution().actorId(); var origin = state.actorLocations().get(actor).supportingSurface();
        for (var endpoint : ShipmentEndpointComposition.replacementPolicy(shipment.receiver()).candidates(state, shipment, origin)) {
                    if (endpoint.containerId().equals(shipment.receiver().containerId()) || shipment.sender().container().filter(endpoint.containerId()::equals).isPresent()) continue;
                    var order = new MovementOrder(shipment.id(), actor, 1, shipment.revision(), List.of(endpoint.station()),
                            TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
                    try {
                        var accepted = ResourceSourceComposition.knowledge(state, shipment.sender(), endpoint).plannedPath(origin, order);
                        return Optional.of(new ShipmentStorageRetargeted(shipment.id(), shipment.revision(), shipment.execution(), origin,
                                endpoint, accepted.size() > 1 ? Optional.of(new PedestrianRouteReceipt(accepted)) : Optional.empty()));
                    } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                        if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
                    }
        }
        return Optional.empty();
    }
}
