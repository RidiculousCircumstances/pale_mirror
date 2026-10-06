package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Versioned owner declaration for a read-only monitor; no second process or pose authority. */
public record ShipmentProgressObligation(SubjectId subject, ActorExecutionId execution, long revision,
        String fingerprint, long budgetTicks, Disposition disposition, Next next) {
    public enum Disposition { ELIGIBLE, HIGHER_PRIORITY_ACTIVITY, RECEIVER_CAPACITY, SERVICE_ACCESS, COURIER_CASUALTY, RECOVERY_UNKNOWN, TERMINAL }
    public enum Next { LOAD, UNLOAD, RECEIVER_ACKNOWLEDGEMENT, CLOSED }
    public static ShipmentProgressObligation describe(FrontierWorldState state, Shipment shipment) {
        var actor = shipment.execution().actorId();
        var authority = state.actorExecutions().actors().get(actor);
        var lease = state.ambientLeases().get(actor);
        boolean closed = shipment.terminal() && shipment.reception().isEmpty();
        var disposition = closed ? Disposition.TERMINAL
                : state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD ? Disposition.COURIER_CASUALTY
                : authority.suspended().filter(shipment.execution()::equals).isPresent() ? Disposition.HIGHER_PRIORITY_ACTIVITY
                : lease != null && lease.status() != AmbientLeaseStatus.CLOSED && lease.status() != AmbientLeaseStatus.HOT ? Disposition.RECOVERY_UNKNOWN
                : !shipment.terminal() && shipment.movementOrder().arrivedAt(state.actorLocations().get(actor).supportingSurface())
                    && !ShipmentStateSupport.serviceAvailable(state, shipment) ? Disposition.SERVICE_ACCESS
                : shipment.status() == Shipment.Status.CARRYING && shipment.reception().isEmpty()
                    && ShipmentStateSupport.coldTransferLots(state, shipment).isEmpty() ? Disposition.RECEIVER_CAPACITY : Disposition.ELIGIBLE;
        var next = closed ? Next.CLOSED : shipment.reception().isPresent() ? Next.RECEIVER_ACKNOWLEDGEMENT
                : shipment.status() == Shipment.Status.AWAITING_LOAD ? Next.LOAD : Next.UNLOAD;
        // The existing movement archetype bounds routes at4096 nodes and20 ticks/edge.
        // This is its safety ceiling, not a new tuned deadline or another movement clock.
        long budget = ActorMovementProcess.maximumJourneyTicks();
        String fingerprint = shipment.status().name() + ":" + shipment.revision() + ":" + shipment.execution().generation()
                + ":" + shipment.pendingPhysicalStep().isPresent() + ":" + shipment.reception().isPresent();
        if (shipment.transportMissionId().isPresent()) {
            var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
            var group = state.unitGroups().groups().get(mission.groupId());
            fingerprint += ":" + mission.stage().name() + ":" + mission.revision() + ":" + group.revision() + ":" + group.phase().name();
        }
        return new ShipmentProgressObligation(shipment.id(), shipment.execution(), shipment.revision(), fingerprint, budget, disposition, next);
    }
}
