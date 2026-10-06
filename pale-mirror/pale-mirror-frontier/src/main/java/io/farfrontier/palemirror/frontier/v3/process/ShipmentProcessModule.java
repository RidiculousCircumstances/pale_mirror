package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Set;

/** Registered transport owner; commercial settlement stays in the commercial reducer. */
final class ShipmentProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.shipment_dispatched", "frontier.shipment_cold_transferred", "frontier.shipment_retired",
            "frontier.shipment_hot_prepared", "frontier.shipment_hot_transferred", "frontier.shipment_hand_custody_observed", "frontier.shipment_receipt_acknowledged", "frontier.shipment_cargo_disposition_observed");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("shipments",
            Set.of("frontier.shipment_dispatch_requested", "frontier.shipment_hot_prepared", "frontier.shipment_hot_transferred", "frontier.shipment_hand_custody_observed", "frontier.shipment_cargo_disposition_observed"),
            Set.of(ShipmentProcess.PROGRESS), TYPES, java.util.stream.Stream.concat(TYPES.stream(), Set.of(
                    "frontier.actor_movement_started", "kernel.schedule_created", "kernel.schedule_rescheduled", "kernel.schedule_cancelled").stream())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()), java.util.stream.Stream.concat(TYPES.stream(),
                            java.util.stream.Stream.of("frontier.shipment_dispatch_requested")).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case ShipmentDispatched value -> ShipmentStateSupport.dispatch(state, event.subject(), value.shipment());
            case ShipmentColdTransferred value -> ShipmentStateSupport.transferCold(state, event.subject(), value.shipmentId(), value.expectedRevision(), value.expectedStatus());
            case ShipmentRetired value -> ShipmentStateSupport.retire(state, event.subject(), value.shipmentId(), value.expectedRevision());
            case ShipmentHotPrepared value -> ShipmentPhysicalStateSupport.prepare(state, event.subject(), value);
            case ShipmentHandCustodyObserved value -> ShipmentPhysicalStateSupport.handCustody(state, event.subject(), value);
            case ShipmentReceiptAcknowledged value -> ShipmentStateSupport.acknowledge(state, event.subject(), value);
            case ShipmentCargoDispositionObserved value -> ShipmentCargoDisposition.apply(state, event.subject(), value);
            case ShipmentHotTransferred value -> ResidentActivityProcess.retargetHotResident(
                    ShipmentPhysicalStateSupport.observed(state, event.subject(), value),
                    state.shipments().shipments().get(value.shipmentId()).execution().actorId(), event.instant().ticks());
            default -> throw new IllegalArgumentException("shipment owner rejects undeclared payload");
        };
    }
    @Override public io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan planCommand(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        try {
            var events = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent>();
            io.farfrontier.palemirror.frontier.v3.api.SubjectId id;
            switch (command.payload()) {
                case ShipmentCargoDispositionObserved value -> {
                    id = value.shipmentId(); ShipmentCargoDisposition.apply(state, id, value);
                    var shipment = state.shipments().shipments().get(id);
                    var movement = state.actorMovements().get(shipment.execution().actorId());
                    if (movement != null) events.add(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(movement.order().actorId(),
                            new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled(ActorMovementProcess.progress(movement, command.submittedAt().ticks() + 1).id())));
                    events.add(ShipmentProcess.wake(id, command.submittedAt().ticks()));
                }
                case ShipmentDispatchRequested value -> {
                    if (state.inventory().economics().require(value.senderId()).ownerKind() != value.senderKind())
                        throw new IllegalArgumentException("shipment request has a forged economic owner kind");
                    return new io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted(ShipmentProcess.dispatch(
                            state, value.senderId(), value.shipment(), command.submittedAt().ticks()));
                }
                case ShipmentHotPrepared value -> {
                    id = value.shipmentId(); ShipmentPhysicalStateSupport.prepare(state, id, value);
                }
                case ShipmentHandCustodyObserved value -> {
                    id = value.shipmentId(); ShipmentPhysicalStateSupport.handCustody(state, id, value);
                }
                case ShipmentHotTransferred value -> {
                    id = value.shipmentId();
                    var preview = ShipmentPhysicalStateSupport.observed(state, value.shipmentId(), value);
                    Shipment shipment = state.shipments().shipments().get(value.shipmentId());
                    if (shipment.status() == Shipment.Status.CARRYING)
                        events.addAll(ShipmentDeliveryNotifications.delivered(preview.shipments().shipments().get(shipment.id()), command.submittedAt().ticks()));
                    else events.addAll(ShipmentDeliveryNotifications.loaded(preview.shipments().shipments().get(shipment.id()), command.submittedAt().ticks()));
                    events.add(ShipmentProcess.wake(shipment.id(), command.submittedAt().ticks()));
                }
                default -> throw new IllegalArgumentException("shipment rejects an undeclared physical command");
            }
            events.addFirst(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(id, command.payload()));
            return new io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted(java.util.List.copyOf(events));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
}
