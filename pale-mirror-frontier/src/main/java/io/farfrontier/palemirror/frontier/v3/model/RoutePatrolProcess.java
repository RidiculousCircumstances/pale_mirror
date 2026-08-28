package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;

/** COLD guard movement which turns existing physical deltas into bounded route evidence. */
final class RoutePatrolProcess {
    private static final long STEP_INTERVAL = 100L;
    private RoutePatrolProcess() { }

    static ScheduledAction start(StrategicTask task, long due) {
        if (task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE) throw new IllegalArgumentException("invalid patrol task schedule");
        return new ScheduledAction(new ScheduleId("schedule:route-patrol-start-" + task.id().value().replace(':', '-')), new SimInstant(due), 0,
                task.id(), "frontier.route_patrol.start", 1);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        Resident guard = settlement.residents().stream().filter(value -> value.role() == ResidentRole.GUARD)
                .filter(value -> state.actorLocations().get(value.id()).condition().status() == ActorLifeStatus.ALIVE)
                .min(Comparator.comparing(Resident::id)).orElse(null);
        if (guard == null || FrontierRouteNetwork.isPassable(state.bootstrap(), state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()), state.physicalDeltas())) {
            return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        }
        RoutePatrol patrol = new RoutePatrol(task.id(), settlement.id(), guard.id(), state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()),
                0, RoutePatrolStatus.EN_ROUTE, java.util.Optional.empty());
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(), new RoutePatrolStarted(patrol)),
                schedule(progress(patrol, action.dueAt().ticks() + STEP_INTERVAL)));
    }

    static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(action.subject());
        if (patrol == null || patrol.status() != RoutePatrolStatus.EN_ROUTE) return List.of();
        StrategicTask task = task(state, patrol.taskId(), StrategicTaskStatus.ACTIVE);
        if (state.actorLocations().get(patrol.guardId()).condition().status() != ActorLifeStatus.ALIVE) {
            return List.of(new ProposedEvent(patrol.settlementId(), new RoutePatrolFailed(patrol.taskId())), transition(task, StrategicTaskStatus.BLOCKED));
        }
        int next = patrol.routeIndex() + 1;
        java.util.Optional<BlockPosition> obstruction = FrontierRouteNetwork.firstObstructionOnSegment(patrol.route(), patrol.routeIndex(), state.physicalDeltas());
        ProposedEvent advanced = new ProposedEvent(patrol.settlementId(), new RoutePatrolAdvanced(patrol.taskId(), next));
        if (obstruction.isPresent()) return List.of(advanced, new ProposedEvent(patrol.settlementId(),
                new RoutePatrolObstructionConfirmed(patrol.taskId(), obstruction.orElseThrow())), transition(task, StrategicTaskStatus.COMPLETED));
        if (next == patrol.route().size() - 1) return List.of(advanced, transition(task, StrategicTaskStatus.COMPLETED));
        return List.of(advanced, schedule(progress(patrol.advance(next), action.dueAt().ticks() + STEP_INTERVAL)));
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RoutePatrolStarted started) {
        RoutePatrol patrol = started.patrol(); StrategicTask task = task(state, patrol.taskId(), StrategicTaskStatus.ACTIVE);
        if (!subject.equals(patrol.settlementId()) || !task.ownerId().equals(subject) || state.strategicPlans().routePatrols().containsKey(patrol.taskId())) {
            throw new IllegalArgumentException("route patrol start has a foreign owner or duplicate task");
        }
        return state.withStrategicPlans(state.strategicPlans().startPatrol(patrol)).withActorLocation(patrol.guardId(), patrol.route().getFirst());
    }

    static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, RoutePatrolAdvanced advanced) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(advanced.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId())) throw new IllegalArgumentException("route patrol advancement has a foreign owner");
        RoutePatrol next = patrol.advance(advanced.routeIndex());
        return state.withStrategicPlans(state.strategicPlans().advancePatrol(advanced.taskId(), advanced.routeIndex()))
                .withActorLocation(patrol.guardId(), next.route().get(next.routeIndex()));
    }

    static FrontierWorldState reduceObstruction(FrontierWorldState state, SubjectId subject, RoutePatrolObstructionConfirmed confirmed) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(confirmed.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId()) || !state.physicalDeltas().containsKey(confirmed.position())) {
            throw new IllegalArgumentException("route patrol obstruction lacks physical evidence");
        }
        return state.withStrategicPlans(state.strategicPlans().confirmPatrolObstruction(confirmed.taskId(), confirmed.position()));
    }

    static FrontierWorldState reduceFailed(FrontierWorldState state, SubjectId subject, RoutePatrolFailed failed) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(failed.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId()) || state.actorLocations().get(patrol.guardId()).condition().status() != ActorLifeStatus.DEAD) {
            throw new IllegalArgumentException("route patrol failure lacks a dead guard");
        }
        return state.withStrategicPlans(state.strategicPlans().failPatrol(failed.taskId()));
    }

    private static ScheduledAction progress(RoutePatrol patrol, long due) { return new ScheduledAction(new ScheduleId("schedule:route-patrol-progress-" + patrol.taskId().value().replace(':', '-')),
            new SimInstant(due), 0, patrol.taskId(), "frontier.route_patrol.progress", 1); }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static StrategicTask task(FrontierWorldState state, SubjectId id, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(id);
        if (task == null || task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE || task.status() != status) throw new IllegalStateException("route patrol has no matching strategic task");
        return task;
    }
}
