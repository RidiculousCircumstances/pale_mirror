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
    public static boolean settled(FrontierWorldState state, UnitGroup group) {
        var journey = group.journey().orElseThrow();
        return group.members().stream().allMatch(member -> !state.actorMovements().containsKey(member.actorId())
                && !state.humanPopulation().meals().containsKey(member.actorId())
                && state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE
                && state.actorLocations().get(member.actorId()).supportingSurface().equals(journey.stations().get(member.actorId())));
    }
    public static Optional<UnitGroupAdvanced> start(FrontierWorldState state, UnitGroup group, long ordinal) {
        var port = UnitGroupMissionPorts.require(group); port.validate(state, group);
        if (!port.mayTravel(state, group, ordinal)) throw new IllegalArgumentException("mission has not authorized this group journey");
        if (group.members().stream().anyMatch(member -> port.execution(state, group, member).isEmpty()
                || state.actorMovements().containsKey(member.actorId()))) return Optional.empty();
        var target = port.destination(state, group, ordinal);
        var departure = group.members().stream().max(Comparator
                .comparingLong((UnitGroup.Member member) -> distance(state.actorLocations().get(member.actorId()).supportingSurface(), target))
                .thenComparing(UnitGroup.Member::actorId)).orElseThrow();
        var origin = state.actorLocations().get(departure.actorId()).supportingSurface();
        var order = new MovementOrder(group.id(), departure.actorId(), ordinal, group.revision() + 1, List.of(target),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        try {
            var route = port.knowledge(state, group).plannedPath(origin, order);
            route = List.copyOf(route.subList(0, Math.min(route.size(), TimedKnownRoute.MAX_SURFACES)));
            return Optional.of(new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.START, ordinal,
                    Optional.of(GroupFormation.first(group, target, route, port.knowledge(state, group))), Optional.of(departure.actorId())));
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return Optional.empty(); }
    }
    public record NavigationReadiness(String status, String reason) {
        public boolean reconsiderOnPlanningProgress() {
            return status.equals("FOUND") || status.equals("NOT_REQUESTED")
                    || status.equals("PLANNING") && reason.equals("PLANNING_QUEUE_CAPACITY");
        }
    }
    /** Retained calculation evidence only; this read must not enqueue work or declare arrival. */
    public static NavigationReadiness navigationReadiness(FrontierWorldState state, UnitGroup group) {
        if (group.phase() == UnitGroup.Phase.CLOSED) return new NavigationReadiness("CLOSED", "GROUP_CLOSED");
        if (group.phase() == UnitGroup.Phase.TRAVELLING && !settled(state, group))
            return new NavigationReadiness("TRAVELLING", "FORMATION_IN_PROGRESS");
        var port = UnitGroupMissionPorts.require(group);
        long ordinal = group.phase() == UnitGroup.Phase.TRAVELLING ? group.goalOrdinal() : group.goalOrdinal() + 1;
        if (!port.mayTravel(state, group, ordinal)) return new NavigationReadiness("MISSION_WAIT", "MISSION_NOT_AUTHORIZED");
        if (group.members().stream().anyMatch(member -> port.execution(state, group, member).isEmpty()
                || state.actorMovements().containsKey(member.actorId())))
            return new NavigationReadiness("PARTICIPANT_WAIT", "EXECUTION_NOT_AVAILABLE");
        var target = port.destination(state, group, ordinal);
        var departure = group.members().stream().max(Comparator
                .comparingLong((UnitGroup.Member member) -> distance(state.actorLocations().get(member.actorId()).supportingSurface(), target))
                .thenComparing(UnitGroup.Member::actorId)).orElseThrow();
        return port.knowledge(state, group).planningEvidence(state.actorLocations().get(departure.actorId()).supportingSurface(), target)
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
                        || !state.actorLocations().get(departure.actorId()).supportingSurface().equals(journey.route().getFirst())
                        || !journey.equals(GroupFormation.first(group, journey.destination(), journey.route(), port.knowledge(state, group)))
                        || group.phase() == UnitGroup.Phase.TRAVELLING && !settled(state, group)
                        || group.members().stream().anyMatch(m -> port.execution(state, group, m).isEmpty()
                            || state.actorMovements().containsKey(m.actorId())))
                    throw new IllegalArgumentException("group journey lacks exact current authority, goal or departure");
                port.knowledge(state, group).requireRoute(journey.route());
                replacement = group.start(value.goalOrdinal(), journey);
            }
            case FRAME -> {
                if (value.goalOrdinal() != group.goalOrdinal() || !settled(state, group)
                        || !port.knowledge(state, group).traversable(List.copyOf(value.journey().orElseThrow().stations().values()))
                        || !value.journey().orElseThrow().equals(GroupFormation.next(group, port.knowledge(state, group))))
                    throw new IllegalArgumentException("group cannot advance before every declared participant settles");
                replacement = group.frame(value.journey().orElseThrow());
            }
            case ARRIVE -> {
                if (value.goalOrdinal() != group.goalOrdinal() || !settled(state, group))
                    throw new IllegalArgumentException("group has not reached its declared formation");
                replacement = group.arrived();
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
        if (group.phase() != UnitGroup.Phase.TRAVELLING) {
            if (group.phase() != UnitGroup.Phase.CLOSED) events.addAll(port.reconsider(state, group, now));
            events.add(new ProposedEvent(group.id(), new ScheduleEffect.Rescheduled(action.id(), progress(group.id(),
                    now + state.bootstrap().ruleset().cadence().terminalLogisticsReviewInterval()))));
            return List.copyOf(events);
        }
        if (settled(state, group)) {
            var journey = group.journey().orElseThrow();
            if (journey.cursor() < journey.route().size() - 1) {
                var next = nextFrame(state, group, port);
                if (next.isPresent())
                    events.add(new ProposedEvent(group.id(), new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.FRAME,
                            group.goalOrdinal(), next, Optional.empty())));
                else start(state, group, group.goalOrdinal()).ifPresent(value -> events.add(new ProposedEvent(group.id(), value)));
            }
            else if (journey.route().getLast().equals(journey.destination()))
                events.add(new ProposedEvent(group.id(), new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.ARRIVE,
                        group.goalOrdinal(), Optional.empty(), Optional.empty())));
            else start(state, group, group.goalOrdinal()).ifPresent(value -> events.add(new ProposedEvent(group.id(), value)));
            if (events.stream().anyMatch(event -> event.payload() instanceof UnitGroupAdvanced value
                    && value.change() == UnitGroupAdvanced.Change.ARRIVE)) events.addAll(port.reconsider(state, group, now));
        } else for (var member : group.members()) {
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
                now + (events.isEmpty() ? state.bootstrap().ruleset().cadence().terminalLogisticsReviewInterval() : 1)))));
        return List.copyOf(events);
    }
    private static Optional<UnitGroup.Journey> nextFrame(FrontierWorldState state, UnitGroup group, UnitGroupMissionPort port) {
        try {
            var knowledge = port.knowledge(state, group);
            var next = GroupFormation.next(group, knowledge);
            return knowledge.traversable(List.copyOf(next.stations().values())) ? Optional.of(next) : Optional.empty();
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            // Ordinary loss of formation space retains the mission and asks the same shared navigator to replan.
            return Optional.empty();
        }
    }
}
