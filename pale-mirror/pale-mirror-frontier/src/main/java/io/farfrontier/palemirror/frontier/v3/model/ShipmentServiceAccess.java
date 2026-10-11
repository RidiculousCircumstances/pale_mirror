package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Shipment declares its access need; the existing shared coordinator arbitrates every family. */
public final class ShipmentServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.COURIER; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var boundary = ServiceAccessCoordinator.boundary(state, pointId);
        var result = new ArrayList<ServiceAccessDemand>();
        for (Shipment shipment : state.shipments().shipments().values()) {
            if (shipment.terminal() || !operationEligible(state, shipment)) continue;
            if (shipment.transportMissionId().map(state.shipments().missions()::get)
                    .flatMap(TransportMission::supplies).filter(load -> !load.complete()).isPresent()) continue;
            boolean occupied = ServiceAccessCoordinator.occupies(state, boundary, shipment.execution().actorId());
            var target = point(shipment);
            // Loading completion ends the source operation. A body still at that source
            // is a spatial blocker, not an incumbent of a finished container transaction.
            if (target.filter(pointId::equals).isPresent())
                result.add(new ServiceAccessDemand(identity(shipment, pointId), priority(), occupied
                        ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH, 0, false));
        }
        return List.copyOf(result);
    }
    static boolean available(FrontierWorldState state, Shipment shipment) {
        return operationEligible(state, shipment) && point(shipment)
                .map(point -> ServiceAccessCoordinator.available(state, identity(shipment, point))).orElse(true);
    }
    private static boolean operationEligible(FrontierWorldState state, Shipment shipment) {
        var execution = state.actorExecutions().actors().get(shipment.execution().actorId());
        if (execution == null || !execution.current().equals(java.util.Optional.of(shipment.execution()))) return false;
        if (shipment.transportMissionId().isPresent()) {
            var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
            if (mission == null) throw new IllegalArgumentException("shipment service lost its exact transport mission");
            var operation = shipment.status() == Shipment.Status.AWAITING_LOAD
                    ? TransportMission.Stage.LOADING : TransportMission.Stage.UNLOADING;
            // A retained cargo claim is not a service turn. During assembly, travel,
            // replenishment or return the workflow owns a different operation.
            if (mission.stage() != operation || mission.replenishment().isPresent()) return false;
        }
        if (shipment.pendingPhysicalStep().isPresent()) return true;
        if (shipment.reception().isPresent()) return false;
        if (shipment.status() == Shipment.Status.CARRYING
                && ReferenceContainerCustody.hasLiveCustody(state, shipment.receiver().containerId()))
            return ShipmentPhysicalStateSupport.destination(state, shipment).isPresent();
        return !ShipmentStateSupport.coldTransferLots(state, shipment).isEmpty();
    }
    public static ServiceAccessDemand.Identity identity(Shipment shipment) {
        return identity(shipment, point(shipment).orElseThrow(() -> new IllegalArgumentException("ground pickup has no container access point")));
    }
    public static java.util.Optional<SubjectId> point(Shipment shipment) {
        return shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.sender().container()
                : java.util.Optional.of(shipment.receiver().containerId());
    }
    private static ServiceAccessDemand.Identity identity(Shipment shipment, SubjectId point) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.COURIER, shipment.id(), shipment.execution().actorId(), point);
    }
}
