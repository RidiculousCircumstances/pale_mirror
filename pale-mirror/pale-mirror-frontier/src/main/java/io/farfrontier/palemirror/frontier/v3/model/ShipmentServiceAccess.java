package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Shipment declares its access need; the existing shared coordinator arbitrates every family. */
final class ShipmentServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.COURIER; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var boundary = ServiceAccessCoordinator.boundary(state, pointId);
        var result = new ArrayList<ServiceAccessDemand>();
        for (Shipment shipment : state.shipments().shipments().values()) {
            if (shipment.terminal()) continue;
            if (shipment.transportMissionId().map(state.shipments().missions()::get)
                    .flatMap(TransportMission::supplies).filter(load -> !load.complete()).isPresent()) continue;
            boolean occupied = ServiceAccessCoordinator.occupies(state, boundary, shipment.execution().actorId());
            SubjectId target = shipment.status() == Shipment.Status.AWAITING_LOAD
                    ? shipment.sender().containerId() : shipment.receiver().containerId();
            // Loading completion ends the source operation. A body still at that source
            // is a spatial blocker, not an incumbent of a finished container transaction.
            if (target.equals(pointId))
                result.add(new ServiceAccessDemand(identity(shipment, pointId), priority(), occupied
                        ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH, 0, false));
        }
        return List.copyOf(result);
    }
    static boolean available(FrontierWorldState state, Shipment shipment) {
        SubjectId point = shipment.status() == Shipment.Status.AWAITING_LOAD
                ? shipment.sender().containerId() : shipment.receiver().containerId();
        return ServiceAccessCoordinator.available(state, identity(shipment, point));
    }
    private static ServiceAccessDemand.Identity identity(Shipment shipment, SubjectId point) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.COURIER, shipment.id(), shipment.execution().actorId(), point);
    }
}
