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
            var retirement = ShipmentRetention.collect(state, 1, atTick);
            if (retirement.events().isEmpty()) throw new IllegalArgumentException("shipment capacity is held by live or unresolved obligations");
            events.addAll(retirement.events()); state = retirement.state();
        }
        ShipmentStateSupport.dispatch(state, sender, shipment);
        events.add(new ProposedEvent(sender, new ShipmentDispatched(shipment)));
        events.add(new ProposedEvent(shipment.id(), new ScheduleEffect.Created(progress(shipment.id(), Math.addExact(atTick, 1)))));
        return List.copyOf(events);
    }
    public static boolean held(FrontierWorldState state, ScheduledAction action) {
        Shipment shipment = state.shipments().shipments().get(action.subject());
        if (shipment == null) return false;
        if (shipment.terminal()) return !ShipmentStateSupport.closedForRetirement(state, shipment);
        if (replenishing(state, shipment)) return !(replenishmentHandoff(state, shipment,
                action.dueAt().ticks()) instanceof ActivityInterruptionPlanner.Ready);
        if (!TransportCargoExecution.selected(state, shipment)) return true;
        if (shipment.transportMissionId().map(state.shipments().missions()::get).flatMap(TransportMission::supplies)
                .filter(load -> !load.complete()).isPresent()) return true;
        if (shipment.transportMissionId().isPresent() && shipment.carrying()) {
            var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
            if (mission == null) throw new IllegalArgumentException("shipment lost its exact transport mission");
            if (mission.stage() != (shipment.status() == Shipment.Status.RETURNING
                    ? TransportMission.Stage.RECOVERING : TransportMission.Stage.UNLOADING)) return true;
        }
        if (state.actorMovements().containsKey(shipment.execution().actorId())) {
            return !(needsStorage(state, shipment) && new ActorMovementInterruptionPlanner().assess(state,
                    shipment.execution().actorId(), Math.max(action.dueAt().ticks(),
                        state.actorMovements().get(shipment.execution().actorId()).issuedAtTick())) instanceof ActivityInterruptionPlanner.Ready);
        }
        var actor = shipment.execution().actorId();
        if (shipment.reception().isPresent()) return true;
        var authority = state.actorExecutions().actors().get(actor);
        if (authority == null || !authority.current().equals(java.util.Optional.of(shipment.execution()))) return true;
        if (state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE
                || ActorExecutionCoordinator.sceneOwns(state, actor)) return true;
        var lease = state.ambientLeases().get(actor);
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED && lease.status() != AmbientLeaseStatus.HOT) return true;
        if (needsStorage(state, shipment)) return false;
        // Goal issuance is allowed under the same HOT courier execution. Only the item effect
        // waits for COLD custody; otherwise a loaded courier could never start its second leg.
        // At the endpoint, service/capacity/custody changes wake this exact continuation.
        // A policy-review retry would keep an already-ready incumbent blocking the doorway.
        return shipment.movementOrder().arrivedAt(state.actorLocations().get(actor).supportingSurface())
                && !ShipmentStateSupport.coldTransferAvailable(state, shipment);
    }
    public static java.util.Set<SubjectId> wakeDependencies(FrontierWorldState state, ScheduledAction action) {
        var shipment = state.shipments().shipments().get(action.subject());
        if (shipment == null) return java.util.Set.of(action.subject());
        var keys = new java.util.HashSet<SubjectId>(); keys.add(shipment.id()); keys.add(shipment.execution().actorId());
        shipment.sender().container().ifPresent(keys::add); keys.add(shipment.receiver().containerId());
        shipment.mobileContainerId().ifPresent(keys::add);
        shipment.transportMissionId().ifPresent(id -> {
            var mission = state.shipments().missions().get(id);
            keys.add(id); keys.add(mission.groupId()); keys.addAll(mission.shipmentIds());
        });
        return java.util.Set.copyOf(keys);
    }
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick) {
        if (!action.kind().equals(PROGRESS) || !action.id().equals(progress(action.subject(), action.dueAt().ticks()).id()))
            throw new IllegalArgumentException("shipment progress lacks its exact schedule");
        Shipment shipment = state.shipments().shipments().get(action.subject());
        if (shipment == null)
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        long now = Math.max(currentTick, action.dueAt().ticks());
        if (ShipmentStateSupport.closedForRetirement(state, shipment)) return ShipmentRetention.retire(shipment, now);
        // Receipt/mission owners retain the held continuation until reception
        // or atomic mission retirement. No polling and no premature cancellation.
        if (held(state, action)) throw new IllegalArgumentException("shipment awaits its exact custody boundary");
        if (replenishing(state, shipment)) {
            var handoff = (ActivityInterruptionPlanner.Ready) replenishmentHandoff(state, shipment, now);
            var events = new java.util.ArrayList<>(handoff.events());
            events.add(TransportMissionProcess.wake(shipment.transportMissionId().orElseThrow(), now));
            events.add(wake(shipment.id(), now));
            return List.copyOf(events);
        }
        if (needsStorage(state, shipment)) {
            var interrupted = new ActorMovementInterruptionPlanner().assessForOwnerTransition(state, shipment.execution(), now);
            if (!(interrupted instanceof ActivityInterruptionPlanner.Ready ready)) return retry(state, shipment, now);
            java.util.Optional<ShipmentStorageRetargeted> replacement;
            try { replacement = ShipmentStorageRetargeted.eligible(ready.following(), shipment); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) { return retry(state, shipment, now); }
            if (replacement.isEmpty()) return retry(state, shipment, now);
            var retarget = replacement.orElseThrow();
            retarget.apply(ready.following(), shipment.id());
            var events = new java.util.ArrayList<>(ready.events()); events.add(new ProposedEvent(shipment.id(), retarget));
            events.add(wake(shipment.id(), now)); return List.copyOf(events);
        }
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
        if (shipment.carrying())
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
    private static boolean needsStorage(FrontierWorldState state, Shipment shipment) {
        return ShipmentStorageRetargeted.unbegun(shipment) && !ShipmentStorageRetargeted.retainedAvailable(state, shipment);
    }
    private static boolean replenishing(FrontierWorldState state, Shipment shipment) {
        return shipment.transportMissionId().map(state.shipments().missions()::get)
                .flatMap(TransportMission::replenishment).isPresent();
    }
    /** Retire only this parcel's approach through the shared interruption protocol.
     * Self-care, refill and other parcel movements retain their own authority. */
    private static ActivityInterruptionPlanner.Assessment replenishmentHandoff(FrontierWorldState state,
            Shipment shipment, long tick) {
        var movement = state.actorMovements().get(shipment.execution().actorId());
        if (movement == null || !(movement.context() instanceof ActorMovementContext.ShipmentLeg leg)
                || !leg.shipmentId().equals(shipment.id()))
            return new ActivityInterruptionPlanner.Waiting(ActivityInterruptionPlanner.Reason.AUTHORITY_HANDOFF);
        return new ActorMovementInterruptionPlanner().assessForOwnerTransition(state, shipment.execution(),
                Math.max(tick, movement.coldTravel().map(TimedKnownRoute::departedAtTick).orElse(movement.issuedAtTick())));
    }
    private ShipmentProcess() { }
}
