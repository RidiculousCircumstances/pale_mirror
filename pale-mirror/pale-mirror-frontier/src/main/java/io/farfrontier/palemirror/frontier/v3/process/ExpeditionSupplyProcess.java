package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** One loading continuation through ordinary movement and custody, without a second scheduler. */
final class ExpeditionSupplyProcess {
    private ExpeditionSupplyProcess() { }
    static boolean assembled(FrontierWorldState state, TransportMission mission) {
        var load = mission.supplies().orElseThrow();
        return load.complete() && load.assemblyStations().entrySet().stream().allMatch(entry ->
                state.actorLocations().get(entry.getKey()).supportingSurface().equals(entry.getValue())
                        && !state.actorMovements().containsKey(entry.getKey())
                        && !state.humanPopulation().meals().containsKey(entry.getKey()));
    }
    static List<ProposedEvent> plan(FrontierWorldState state, TransportMission mission, ScheduledAction action, long now) {
        var load = mission.supplies().orElseThrow();
        var events = new ArrayList<ProposedEvent>();
        var group = state.unitGroups().groups().get(mission.groupId());
        if (load.needsReplan() || load.complete() && !ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now)) {
            ExpeditionSupplyAuthority.reconsider(state, mission, now).ifPresent(replacement -> events.add(new ProposedEvent(mission.id(),
                    new ExpeditionSupplyReplanned(mission.id(), load.revision(), replacement))));
            events.add(new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), TransportMissionProcess.progress(mission.id(),
                    now + (events.isEmpty() ? state.bootstrap().ruleset().cadence().transportReviewInterval() : 1)))));
            return List.copyOf(events);
        }
        for (var member : group.members()) {
            var actor = member.actorId();
            if (load.allocations().stream().anyMatch(a -> a.actorId().equals(actor) && !a.loaded())
                    || state.actorMovements().containsKey(actor) || state.humanPopulation().meals().containsKey(actor)
                    || load.complete() && mission.shipmentIds().stream().map(state.shipments().shipments()::get)
                        .anyMatch(shipment -> shipment.execution().actorId().equals(actor) && shipment.status() == Shipment.Status.AWAITING_LOAD)) continue;
            var order = ExpeditionSupplyAuthority.assemblyOrder(mission, actor);
            var position = state.actorLocations().get(actor).supportingSurface();
            if (order.arrivedAt(position)) continue;
            var execution = io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts.require(group).execution(state, group, member);
            if (execution.isEmpty()) continue;
            var movement = new ActorMovement(order, now, new ActorMovementContext.ExpeditionAssembly(mission.id()), execution.orElseThrow());
            try {
                ActorMovementProviders.require(movement).route(state, movement, position);
                events.add(new ProposedEvent(actor, new ActorMovementStarted(movement)));
                events.add(new ProposedEvent(actor, new ScheduleEffect.Created(ActorMovementProcess.progress(movement, now + 1))));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { /* Common navigation retains the blocked goal. */ }
        }
        load.next().ifPresent(a -> collect(state, mission, load, a, now, events));
        events.add(new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), TransportMissionProcess.progress(mission.id(),
                now + (events.isEmpty() ? state.bootstrap().ruleset().cadence().transportReviewInterval() : 1)))));
        return List.copyOf(events);
    }
    private static void collect(FrontierWorldState state, TransportMission mission,
            io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyLoad load,
            io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyLoad.Allocation a,
            long now, List<ProposedEvent> events) {
        var order = load.order(mission.id(), mission.sender(), a);
        var execution = ExpeditionSupplyAuthority.execution(state, mission, a);
        boolean permitted = a.pending().isEmpty() && execution.isPresent() && !state.actorMovements().containsKey(a.actorId())
                && ServiceAccessCoordinator.available(state, ExpeditionSupplyServiceAccess.identity(mission, a));
        if (permitted && !state.actorLocations().get(a.actorId()).supportingSurface().equals(order.station())) {
            var movement = new ActorMovement(order.movementOrder(), now,
                    new ActorMovementContext.ExpeditionSupply(mission.id(), a.claimId()), execution.orElseThrow());
            try {
                ActorMovementProviders.require(movement).route(state, movement, state.actorLocations().get(a.actorId()).supportingSurface());
                events.add(new ProposedEvent(a.actorId(), new ActorMovementStarted(movement)));
                events.add(new ProposedEvent(a.actorId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, now + 1))));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { /* Retain named load; common navigation owns reconsideration. */ }
        } else if (permitted && ActorExecutionCoordinator.coldAvailable(state, a.actorId())
                && !ReferenceContainerCustody.hasLiveCustody(state, mission.sender().containerId())
                && !ReferenceContainerCustody.blocksCanonicalUse(state, mission.sender().containerId())) {
            ExpeditionSupplyAuthority.coldLoaded(state, mission.id(), a.claimId());
            events.add(new ProposedEvent(mission.id(), new ExpeditionSupplyColdLoaded(mission.id(), a.claimId())));
        }
    }
}
