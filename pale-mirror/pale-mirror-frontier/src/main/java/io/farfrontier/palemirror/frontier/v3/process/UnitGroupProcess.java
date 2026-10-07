package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Generic rendezvous/formation coordination. Mission policy enters only through a registered port. */
public final class UnitGroupProcess {
    public static final String PROGRESS = UnitGroupContinuation.PROGRESS;
    private UnitGroupProcess() { }
    public static ScheduledAction progress(SubjectId group, long tick) {
        return UnitGroupContinuation.at(group, tick);
    }
    public static ProposedEvent wake(SubjectId group, long tick) {
        return UnitGroupContinuation.wake(group, tick);
    }
    private static List<UnitGroup.Member> living(FrontierWorldState state, UnitGroup group) {
        return group.members().stream().filter(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE).toList();
    }
    public static boolean settled(FrontierWorldState state, UnitGroup group) {
        var journey = group.journey().orElseThrow();
        return group.members().stream().allMatch(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.DEAD
                || !state.actorMovements().containsKey(member.actorId())
                && !state.humanPopulation().meals().containsKey(member.actorId())
                && state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE
                && state.actorLocations().get(member.actorId()).supportingSurface().equals(journey.stations().get(member.actorId())));
    }
    public static Optional<UnitGroupAdvanced> start(FrontierWorldState state, UnitGroup group, long ordinal) {
        var port = UnitGroupMissionPorts.require(group); port.validate(state, group);
        if (!port.mayTravel(state, group, ordinal)) throw new IllegalArgumentException("mission has not authorized this group journey");
        if (group.phase() == UnitGroup.Phase.TRAVELLING && !settled(state, group)) return Optional.empty();
        if (group.members().stream().anyMatch(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE
                && (port.execution(state, group, member).isEmpty() || state.actorMovements().containsKey(member.actorId())))) return Optional.empty();
        var target = port.destination(state, group, ordinal);
        var availableDeparture = group.members().stream().filter(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE).max(Comparator
                .comparingLong((UnitGroup.Member member) -> distance(state.actorLocations().get(member.actorId()).supportingSurface(), target))
                .thenComparing(UnitGroup.Member::actorId));
        if (availableDeparture.isEmpty()) return Optional.empty();
        var departure = availableDeparture.orElseThrow();
        var origin = state.actorLocations().get(departure.actorId()).supportingSurface();
        var order = new MovementOrder(group.id(), departure.actorId(), ordinal, group.revision() + 1, List.of(target),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        try {
            var route = port.knowledge(state, group).plannedPath(origin, order);
            route = List.copyOf(route.subList(0, Math.min(route.size(), TimedKnownRoute.MAX_SURFACES)));
            return Optional.of(new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.START, ordinal,
                    Optional.of(GroupFormation.first(living(state, group), target, route, port.knowledge(state, group), port.travelPolicy(state, group).spacing())), Optional.of(departure.actorId())));
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return Optional.empty(); }
    }
    public record NavigationReadiness(String status, String reason) {
    }
    /** Retained calculation evidence only; this read must not enqueue work or declare arrival. */
    public static NavigationReadiness navigationReadiness(FrontierWorldState state, UnitGroup group) {
        if (group.phase() == UnitGroup.Phase.CLOSED) return new NavigationReadiness("CLOSED", "GROUP_CLOSED");
        if (group.phase() == UnitGroup.Phase.TRAVELLING && !settled(state, group))
            return new NavigationReadiness("TRAVELLING", "FORMATION_IN_PROGRESS");
        var port = UnitGroupMissionPorts.require(group);
        long ordinal = group.phase() == UnitGroup.Phase.TRAVELLING ? group.goalOrdinal() : group.goalOrdinal() + 1;
        if (!port.mayTravel(state, group, ordinal)) return new NavigationReadiness("MISSION_WAIT", "MISSION_NOT_AUTHORIZED");
        if (group.members().stream().anyMatch(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE
                && (port.execution(state, group, member).isEmpty() || state.actorMovements().containsKey(member.actorId()))))
            return new NavigationReadiness("PARTICIPANT_WAIT", "EXECUTION_NOT_AVAILABLE");
        var target = port.destination(state, group, ordinal);
        var departure = group.members().stream().filter(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE).max(Comparator
                .comparingLong((UnitGroup.Member member) -> distance(state.actorLocations().get(member.actorId()).supportingSurface(), target))
                .thenComparing(UnitGroup.Member::actorId));
        if (departure.isEmpty()) return new NavigationReadiness("PARTICIPANT_WAIT", "NO_SURVIVING_PARTICIPANTS");
        return port.knowledge(state, group).planningEvidence(state.actorLocations().get(departure.orElseThrow().actorId()).supportingSurface(), target)
                .map(result -> new NavigationReadiness(result.status().name(), result.reason()))
                .orElseGet(() -> new NavigationReadiness("NOT_REQUESTED", "NO_CURRENT_CALCULATION_EVIDENCE"));
    }
    private static long distance(SurfaceAnchor left, SurfaceAnchor right) {
        return Math.abs((long) left.x() - right.x()) + Math.abs((long) left.z() - right.z());
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, UnitGroupAdvanced value) {
        var group = state.unitGroups().groups().get(value.groupId());
        if (group == null || !subject.equals(group.id()) || group.revision() != value.expectedRevision())
            throw new IllegalArgumentException("group transition has a foreign or stale predecessor");
        var port = UnitGroupMissionPorts.require(group); port.validate(state, group);
        UnitGroup replacement;
        switch (value.change()) {
            case START -> {
                var journey = value.journey().orElseThrow();
                var departure = group.member(value.departureActor().orElseThrow());
                if (!port.mayTravel(state, group, value.goalOrdinal())
                        || !port.destination(state, group, value.goalOrdinal()).equals(journey.destination())
                        || state.actorLocations().get(departure.actorId()).condition().status() != ActorLifeStatus.ALIVE
                        || !state.actorLocations().get(departure.actorId()).supportingSurface().equals(journey.route().getFirst())
                        || !journey.equals(GroupFormation.first(living(state, group), journey.destination(), journey.route(), port.knowledge(state, group), port.travelPolicy(state, group).spacing()))
                        || group.phase() == UnitGroup.Phase.TRAVELLING && !settled(state, group)
                        || group.members().stream().anyMatch(m -> state.actorLocations().get(m.actorId()).condition().status() == ActorLifeStatus.ALIVE
                            && (port.execution(state, group, m).isEmpty() || state.actorMovements().containsKey(m.actorId()))))
                    throw new IllegalArgumentException("group journey lacks exact current authority, goal or departure");
                port.knowledge(state, group).requireRoute(journey.route());
                replacement = group.start(value.goalOrdinal(), journey);
            }
            case ARRIVE -> {
                if (value.goalOrdinal() != group.goalOrdinal() || !settled(state, group))
                    throw new IllegalArgumentException("group has not reached its declared formation");
                replacement = group.arrived();
            }
            case STOP -> {
                if (value.goalOrdinal() != group.goalOrdinal() || !port.requestsJourneyStop(state, group)
                        || group.members().stream().anyMatch(m -> state.actorMovements().containsKey(m.actorId())
                            || state.humanPopulation().meals().containsKey(m.actorId())))
                    throw new IllegalArgumentException("journey cancellation lacks purpose authorization or movement acknowledgements");
                replacement = group.stopJourney();
            }
            case CLOSE -> {
                if (value.goalOrdinal() != group.goalOrdinal() || !port.mayClose(state, group)
                        || group.members().stream().anyMatch(m -> state.actorMovements().containsKey(m.actorId())
                            || state.humanPopulation().meals().containsKey(m.actorId())))
                    throw new IllegalArgumentException("group closure retains an unfinished participant activity");
                replacement = group.close();
            }
            default -> throw new IllegalArgumentException("unsupported group transition");
        }
        var update = FrontierWorldStateUpdate.begin().unitGroups(state.unitGroups().replace(group, replacement));
        if (replacement.phase() == UnitGroup.Phase.CLOSED) {
            var executions = state.actorExecutions();
            for (var member : group.members()) {
                var current = executions.actors().get(member.actorId());
                if (current != null && current.current().isPresent()) {
                    var id = current.current().orElseThrow();
                    if (id.activityKind() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.GROUP_MEMBER
                            && id.activityOwnerId().equals(group.id())) executions = executions.finish(id);
                }
            }
            update.actorExecutions(executions);
        }
        return state.withChanges(update);
    }
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick) {
        var group = state.unitGroups().groups().get(action.subject()); long now = Math.max(currentTick, action.dueAt().ticks());
        if (group == null)
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        var port = UnitGroupMissionPorts.require(group); port.validate(state, group);
        var events = new ArrayList<ProposedEvent>();
        if (group.phase() != UnitGroup.Phase.CLOSED && group.phase() != UnitGroup.Phase.TRAVELLING
                && group.members().stream().allMatch(m -> state.actorLocations().get(m.actorId()).condition().status() == ActorLifeStatus.DEAD)
                && port.mayClose(state, group)) {
            events.add(new ProposedEvent(group.id(), new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.CLOSE,
                    group.goalOrdinal(), Optional.empty(), Optional.empty())));
            events.addAll(port.reconsider(state, group, now));
            events.add(new ProposedEvent(group.id(), new ScheduleEffect.Cancelled(action.id())));
            return List.copyOf(events);
        }
        if (!port.mayContinueMovement(state, group)) {
            // Purpose owns the rest decision. Common movement acknowledges each exact current
            // position before stock policy can interact; nobody is teleported to a rendezvous.
            for (var member : group.members()) {
                var movement = state.actorMovements().get(member.actorId());
                if (movement == null || !(movement.context() instanceof ActorMovementContext.GroupLeg leg)
                        || !leg.groupId().equals(group.id()) || state.actorLocations().get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE) continue;
                var stop = new ActorMovementInterruptionPlanner().assess(state, member.actorId(), now);
                if (stop instanceof ActivityInterruptionPlanner.Ready ready)
                    events.addAll(ready.events().stream().filter(event -> !(event.payload() instanceof ScheduleEffect.Rescheduled wake
                            && wake.scheduleId().equals(action.id()))).toList());
            }
            if (group.phase() == UnitGroup.Phase.TRAVELLING && port.requestsJourneyStop(state, group)
                    && group.members().stream().noneMatch(m -> state.actorMovements().containsKey(m.actorId())
                        || state.humanPopulation().meals().containsKey(m.actorId())))
                events.add(new ProposedEvent(group.id(), new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.STOP,
                        group.goalOrdinal(), Optional.empty(), Optional.empty())));
            events.addAll(port.reconsider(state, group, now));
            events.add(new ProposedEvent(group.id(), new ScheduleEffect.Rescheduled(action.id(), progress(group.id(),
                    now + (events.stream().anyMatch(event -> event.payload() instanceof ActorMovementInterrupted)
                        ? 1 : state.bootstrap().ruleset().cadence().transportReviewInterval())))));
            return List.copyOf(events);
        }
        if (group.phase() != UnitGroup.Phase.TRAVELLING) {
            if (group.phase() != UnitGroup.Phase.CLOSED) events.addAll(port.reconsider(state, group, now));
            events.add(new ProposedEvent(group.id(), new ScheduleEffect.Rescheduled(action.id(), progress(group.id(),
                    now + state.bootstrap().ruleset().cadence().transportReviewInterval()))));
            return List.copyOf(events);
        }
        if (settled(state, group)) {
            var journey = group.journey().orElseThrow();
            if (journey.route().getLast().equals(journey.destination()))
                events.add(new ProposedEvent(group.id(), new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.ARRIVE,
                        group.goalOrdinal(), Optional.empty(), Optional.empty())));
            else start(state, group, group.goalOrdinal()).ifPresent(value -> events.add(new ProposedEvent(group.id(), value)));
            if (events.stream().anyMatch(event -> event.payload() instanceof UnitGroupAdvanced value
                    && value.change() == UnitGroupAdvanced.Change.ARRIVE)) events.addAll(port.reconsider(state, group, now));
        } else for (var member : group.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE) continue;
            var target = group.journey().orElseThrow().stations().get(member.actorId());
            if (state.actorMovements().containsKey(member.actorId()) || state.actorLocations().get(member.actorId()).supportingSurface().equals(target)) continue;
            var execution = port.execution(state, group, member);
            if (execution.isEmpty()) continue;
            var order = new MovementOrder(group.id(), member.actorId(), group.goalOrdinal(), group.revision(), List.of(target),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            var movement = new ActorMovement(order, now, new ActorMovementContext.GroupLeg(group.id(), group.revision()), execution.orElseThrow());
            events.add(new ProposedEvent(member.actorId(), new ActorMovementStarted(movement)));
            events.add(new ProposedEvent(member.actorId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, now + 1))));
        }
        events.add(new ProposedEvent(group.id(), new ScheduleEffect.Rescheduled(action.id(), progress(group.id(),
                now + (events.isEmpty() ? state.bootstrap().ruleset().cadence().transportReviewInterval() : 1)))));
        return List.copyOf(events);
    }
}
