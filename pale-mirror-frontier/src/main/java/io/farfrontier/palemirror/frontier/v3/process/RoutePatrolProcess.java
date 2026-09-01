package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;

/** COLD guard movement which turns existing physical deltas into bounded route evidence. */
public final class RoutePatrolProcess {
    private RoutePatrolProcess() { }

    static ScheduledAction start(StrategicTask task, long due) {
        if (task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE) throw new IllegalArgumentException("invalid patrol task schedule");
        return new ScheduledAction(new ScheduleId("schedule:route-patrol-start-" + task.id().value().replace(':', '-')), new SimInstant(due), 0,
                task.id(), "frontier.route_patrol.start", 1);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        List<ResidentProfile> candidates = FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), ResidentProfession.SECURITY_WORKER);
        if (state.routeTopology().supplyPassable(state.bootstrap(), settlement.id())) {
            return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        }
        if (candidates.size() < 2) {
            return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        }
        ResidentProfile leader = candidates.getFirst();
        RouteUnitManifest unit = RouteUnitManifest.patrol(task.id(), leader.id(), List.of(candidates.get(1).id()));
        RoutePatrol patrol = new RoutePatrol(task.id(), settlement.id(), unit, state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()),
                0, RoutePatrolStatus.EN_ROUTE, java.util.Optional.empty());
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(), new RoutePatrolStarted(patrol)),
                schedule(progress(patrol, action.dueAt().ticks() + state.bootstrap().ruleset().cadence().routePatrolStepInterval())));
    }

    static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(action.subject());
        if (patrol == null || patrol.status() != RoutePatrolStatus.EN_ROUTE) return List.of();
        StrategicTask task = task(state, patrol.taskId(), StrategicTaskStatus.ACTIVE);
        if (patrol.memberIds().stream().noneMatch(member -> state.actorLocations().get(member).condition().status() == ActorLifeStatus.ALIVE)) {
            return List.of(new ProposedEvent(patrol.settlementId(), new RoutePatrolFailed(patrol.taskId())), transition(task, StrategicTaskStatus.BLOCKED));
        }
        int next = patrol.routeIndex() + 1;
        java.util.Optional<BlockPosition> obstruction = FrontierRouteNetwork.firstObstructionOnCarriagewaySegment(patrol.route(), patrol.routeIndex(), state.physicalDeltas());
        ProposedEvent advanced = new ProposedEvent(patrol.settlementId(), new RoutePatrolAdvanced(patrol.taskId(), next));
        if (obstruction.isPresent()) {
            BlockPosition confirmed = obstruction.orElseThrow();
            ScheduledAction reconsideration = StrategicObjectiveProcess.routeReconsideration(patrol.settlementId(), confirmed, "confirmed",
                    Math.addExact(action.dueAt().ticks(), 1L));
            return List.of(advanced, new ProposedEvent(patrol.settlementId(), new RoutePatrolObstructionConfirmed(patrol.taskId(), confirmed)),
                    transition(task, StrategicTaskStatus.COMPLETED), new ProposedEvent(patrol.settlementId(), new ScheduleEffect.Created(reconsideration)));
        }
        if (next == patrol.route().size() - 1) return List.of(advanced, transition(task, StrategicTaskStatus.COMPLETED));
        return List.of(advanced, schedule(progress(patrol.advance(next), action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().routePatrolStepInterval())));
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RoutePatrolStarted started) {
        RoutePatrol patrol = started.patrol(); StrategicTask task = task(state, patrol.taskId(), StrategicTaskStatus.ACTIVE);
        if (!subject.equals(patrol.settlementId()) || !task.ownerId().equals(subject) || state.strategicPlans().routePatrols().containsKey(patrol.taskId())) {
            throw new IllegalArgumentException("route patrol start has a foreign owner or duplicate task");
        }
        FrontierWorldState next = state.withStrategicPlans(state.strategicPlans().startPatrol(patrol));
        for (SubjectId member : patrol.memberIds()) next = next.withActorBody(member, BodyPosition.above(new SurfaceAnchor(patrol.route().getFirst())));
        return next;
    }

    static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, RoutePatrolAdvanced advanced) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(advanced.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId())) throw new IllegalArgumentException("route patrol advancement has a foreign owner");
        RoutePatrol next = patrol.advance(advanced.routeIndex());
        FrontierWorldState updated = state.withStrategicPlans(state.strategicPlans().advancePatrol(advanced.taskId(), advanced.routeIndex()));
        for (SubjectId member : patrol.memberIds()) updated = updated.withActorBody(member, BodyPosition.above(new SurfaceAnchor(next.route().get(next.routeIndex()))));
        return updated;
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
        if (patrol == null || !subject.equals(patrol.settlementId()) || patrol.memberIds().stream()
                .anyMatch(member -> state.actorLocations().get(member).condition().status() == ActorLifeStatus.ALIVE)) {
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
