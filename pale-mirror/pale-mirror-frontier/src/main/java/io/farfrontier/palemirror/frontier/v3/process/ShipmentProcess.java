package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.List;

/** Semantic handoffs drive the journey; there is no per-cell courier tick or second body clock. */
public final class ShipmentProcess {
    public static final String PROGRESS = ShipmentContinuation.PROGRESS;
    public static ScheduledAction progress(SubjectId shipment, long atTick) {
        return ShipmentContinuation.at(shipment, atTick);
    }
    public static ProposedEvent wake(SubjectId shipment, long atTick) {
        return ShipmentContinuation.wake(shipment, atTick);
    }
    public static List<ProposedEvent> dispatch(FrontierWorldState state, SubjectId sender, Shipment shipment, long atTick) {
        var events = new java.util.ArrayList<ProposedEvent>();
        if (state.shipments().shipments().size() == ShipmentState.MAX_SHIPMENTS) {
            var retained = state;
            var closed = state.shipments().shipments().values().stream()
                    .filter(s -> ShipmentStateSupport.closedForRetirement(retained, s)).sorted(java.util.Comparator.comparing(Shipment::id))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("shipment capacity is held by live or unresolved obligations"));
            events.add(new ProposedEvent(closed.id(), new ScheduleEffect.Cancelled(progress(closed.id(), atTick).id())));
            events.add(new ProposedEvent(closed.id(), new ShipmentRetired(closed.id(), closed.revision())));
            state = ShipmentStateSupport.retire(state, closed.id(), closed.id(), closed.revision());
        }
        ShipmentStateSupport.dispatch(state, sender, shipment);
        events.add(new ProposedEvent(sender, new ShipmentDispatched(shipment)));
        events.add(new ProposedEvent(shipment.id(), new ScheduleEffect.Created(progress(shipment.id(), Math.addExact(atTick, 1)))));
        return List.copyOf(events);
    }
    public static boolean held(FrontierWorldState state, ScheduledAction action) {
        Shipment shipment = state.shipments().shipments().get(action.subject());
        if (shipment == null || shipment.terminal()) return false;
        if (shipment.transportMissionId().map(state.shipments().missions()::get).flatMap(TransportMission::supplies)
                .filter(load -> !load.complete()).isPresent()) return true;
        if (shipment.transportMissionId().isPresent() && shipment.status() == Shipment.Status.CARRYING) {
            var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
            if (mission == null) throw new IllegalArgumentException("shipment lost its exact transport mission");
            if (mission.stage() != TransportMission.Stage.UNLOADING) return true;
        }
        if (state.actorMovements().containsKey(shipment.execution().actorId())) return true;
        var actor = shipment.execution().actorId();
        if (shipment.reception().isPresent()) return true;
        var authority = state.actorExecutions().actors().get(actor);
        if (authority == null || !authority.current().equals(java.util.Optional.of(shipment.execution()))) return true;
        if (state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE
                || ActorExecutionCoordinator.sceneOwns(state, actor)) return true;
        var lease = state.ambientLeases().get(actor);
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED && lease.status() != AmbientLeaseStatus.HOT) return true;
        // Goal issuance is allowed under the same HOT courier execution. Only the item effect
        // waits for COLD custody; otherwise a loaded courier could never start its second leg.
        return shipment.movementOrder().arrivedAt(state.actorLocations().get(actor).supportingSurface())
                && !ActorExecutionCoordinator.coldAvailable(state, actor);
    }
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick) {
        if (!action.kind().equals(PROGRESS) || !action.id().equals(progress(action.subject(), action.dueAt().ticks()).id()))
            throw new IllegalArgumentException("shipment progress lacks its exact schedule");
        Shipment shipment = state.shipments().shipments().get(action.subject());
        if (shipment == null || shipment.terminal() && shipment.transportMissionId().isEmpty())
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        long now = Math.max(currentTick, action.dueAt().ticks());
        if (shipment.terminal()) return List.of(new ProposedEvent(shipment.id(), new ScheduleEffect.Rescheduled(action.id(), progress(shipment.id(),
                now + state.bootstrap().ruleset().cadence().transportReviewInterval()))));
        if (held(state, action)) throw new IllegalArgumentException("shipment awaits its exact custody boundary");
        var order = shipment.movementOrder();
        if (!order.arrivedAt(state.actorLocations().get(order.actorId()).supportingSurface())) {
            var movement = new ActorMovement(order, now, new ActorMovementContext.ShipmentLeg(shipment.id(), shipment.revision()), shipment.execution());
            try { ActorMovementProviders.require(movement).route(state, movement, state.actorLocations().get(order.actorId()).supportingSurface()); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return retry(state, shipment, now); }
            return List.of(new ProposedEvent(order.actorId(), new ActorMovementStarted(movement)),
                    new ProposedEvent(order.actorId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, Math.addExact(now, 1)))),
                    new ProposedEvent(shipment.id(), new ScheduleEffect.Rescheduled(action.id(), progress(shipment.id(), Math.addExact(now, 1)))));
        }
        if (!ShipmentStateSupport.coldTransferAvailable(state, shipment)) return retry(state, shipment, now);
        var events = new java.util.ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(shipment.id(), new ShipmentColdTransferred(shipment.id(), shipment.revision(), shipment.status())));
        if (shipment.status() == Shipment.Status.CARRYING)
            events.addAll(ShipmentDeliveryNotifications.delivered(ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(),
                    shipment.revision(), shipment.status()).shipments().shipments().get(shipment.id()), now));
        else events.addAll(ShipmentDeliveryNotifications.loaded(ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(),
                shipment.revision(), shipment.status()).shipments().shipments().get(shipment.id()), now));
        events.add(wake(shipment.id(), now));
        return List.copyOf(events);
    }
    private static List<ProposedEvent> retry(FrontierWorldState state, Shipment shipment, long now) {
        var action = progress(shipment.id(), Math.addExact(now,
                state.bootstrap().ruleset().cadence().transportReviewInterval()));
        return List.of(new ProposedEvent(shipment.id(), new ScheduleEffect.Rescheduled(action.id(), action)));
    }
    private ShipmentProcess() { }
}
