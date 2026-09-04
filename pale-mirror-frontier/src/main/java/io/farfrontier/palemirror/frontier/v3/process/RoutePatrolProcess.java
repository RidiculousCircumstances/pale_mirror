package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.List;

/** COLD guard movement which turns existing physical deltas into bounded route evidence. */
public final class RoutePatrolProcess {
    /** Bounded reactive inspection must not sit behind unrelated recurring world work. */
    static final int REACTIVE_INSPECTION_PRIORITY = 100;
    private RoutePatrolProcess() { }

    static ScheduledAction start(StrategicTask task, long due) {
        if (task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE) throw new IllegalArgumentException("invalid patrol task schedule");
        return new ScheduledAction(new ScheduleId("schedule:route-patrol-start-" + task.id().value().replace(':', '-')), new SimInstant(due), REACTIVE_INSPECTION_PRIORITY,
                task.id(), "frontier.route_patrol.start", 1);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        List<ResidentProfile> candidates = FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), ResidentProfession.SECURITY_WORKER);
        if (task.operationTarget().isEmpty() && state.routeTopology().supplyPassable(state.bootstrap(), settlement.id())) {
            return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        }
        if (candidates.size() < 2) {
            return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        }
        RoutePatrol patrol = selectIngressCapablePatrol(state, task, settlement, candidates);
        if (patrol == null) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(), new RoutePatrolStarted(patrol)),
                schedule(progress(patrol, Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().routePatrolStepInterval()))));
    }

    static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(action.subject());
        if (patrol == null || !patrol.active()) return List.of();
        if (state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && FrontierSceneBehaviors.isRoutePatrol(lease)
                && FrontierSceneBehaviors.routePatrol(lease).taskId().equals(patrol.taskId()))) return List.of();
        StrategicTask task = task(state, patrol.taskId(), StrategicTaskStatus.ACTIVE);
        RoutePatrol current = patrol;
        List<ProposedEvent> events = new ArrayList<>();
        for (int advance = 0; advance < PatrolTravel.MAX_COLD_ADVANCES; advance++) {
            if (current.memberIds().stream().noneMatch(member -> state.actorLocations().get(member).condition().status() == ActorLifeStatus.ALIVE)) {
                events.add(new ProposedEvent(current.settlementId(), new RoutePatrolFailed(current.taskId())));
                events.add(transition(task, StrategicTaskStatus.BLOCKED));
                return List.copyOf(events);
            }
            java.util.Optional<BlockPosition> obstruction = FrontierRouteNetwork.firstObstructionOnCarriagewaySegment(current.route(), current.routeIndex(), state.physicalDeltas());
            if (obstruction.isPresent()) {
                BlockPosition confirmed = obstruction.orElseThrow();
                ScheduledAction reconsideration = StrategicObjectiveProcess.routeReconsideration(current.settlementId(), confirmed, "confirmed",
                        Math.addExact(action.dueAt().ticks(), 1L));
                events.add(new ProposedEvent(current.settlementId(), new RoutePatrolObstructionConfirmed(current.taskId(), confirmed)));
                events.add(transition(task, StrategicTaskStatus.COMPLETED));
                events.add(new ProposedEvent(current.settlementId(), new ScheduleEffect.Created(reconsideration)));
                return List.copyOf(events);
            }
            List<SubjectId> advances = current.safeAdvances();
            if (advances.isEmpty()) {
                events.add(new ProposedEvent(current.settlementId(), new RoutePatrolBlocked(current.taskId())));
                events.add(transition(task, StrategicTaskStatus.BLOCKED));
                return List.copyOf(events);
            }
            SubjectId actor = advances.getFirst();
            current = current.advance(actor);
            events.add(new ProposedEvent(current.settlementId(), new RoutePatrolAdvanced(current.taskId(), actor)));
            if (current.status() == RoutePatrolStatus.ROUTE_CLEAR) {
                events.add(transition(task, StrategicTaskStatus.COMPLETED));
                return List.copyOf(events);
            }
        }
        events.add(schedule(progress(current, Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().routePatrolStepInterval()))));
        return List.copyOf(events);
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RoutePatrolStarted started) {
        RoutePatrol patrol = started.patrol(); StrategicTask task = task(state, patrol.taskId(), StrategicTaskStatus.ACTIVE);
        if (!subject.equals(patrol.settlementId()) || !task.ownerId().equals(subject) || state.strategicPlans().routePatrols().containsKey(patrol.taskId())) {
            throw new IllegalArgumentException("route patrol start has a foreign owner or duplicate task");
        }
        if (patrol.status() != RoutePatrolStatus.ASSEMBLING
                || !patrol.inspectionRoute().equals(RoutePatrol.inspectionTopology(state, task, FrontierWorldStateSupport.settlement(state.bootstrap(), patrol.settlementId())))) {
            throw new IllegalArgumentException("route patrol start must retain the current canonical route");
        }
        for (var entry : patrol.assembly().bodies().entrySet()) if (!state.actorLocations().get(entry.getKey()).body().equals(entry.getValue())) {
            throw new IllegalArgumentException("route patrol ingress must begin at each exact current resident body");
        }
        return state.withStrategicPlans(state.strategicPlans().startPatrol(patrol));
    }

    /** Shared deterministic reducer boundary for the engine and test-only read-only fixture assembly. */
    public static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, RoutePatrolAdvanced advanced) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(advanced.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId())) throw new IllegalArgumentException("route patrol advancement has a foreign owner");
        if (!patrol.safeAdvances().contains(advanced.actorId())) throw new IllegalArgumentException("route patrol advance does not name a safe retained member");
        RoutePatrol next = patrol.advance(advanced.actorId());
        BodyPosition body = next.status() == RoutePatrolStatus.ASSEMBLING ? next.assembly().bodies().get(advanced.actorId())
                : next.travel().bodies().get(advanced.actorId());
        if (!state.actorLocations().get(advanced.actorId()).body().equals(patrol.status() == RoutePatrolStatus.ASSEMBLING
                ? patrol.assembly().bodies().get(advanced.actorId()) : patrol.travel().bodies().get(advanced.actorId()))) {
            throw new IllegalArgumentException("route patrol member body diverged before retained advance");
        }
        return state.withStrategicPlans(state.strategicPlans().advancePatrol(advanced.taskId(), advanced.actorId())).withActorBody(advanced.actorId(), body);
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

    static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, RoutePatrolBlocked blocked) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(blocked.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId()) || !patrol.active()) throw new IllegalArgumentException("route patrol block has a foreign owner");
        return state.withStrategicPlans(state.strategicPlans().blockPatrol(blocked.taskId()));
    }

    static ScheduledAction progress(RoutePatrol patrol, long due) { return new ScheduledAction(new ScheduleId("schedule:route-patrol-progress-" + patrol.taskId().value().replace(':', '-')),
            new SimInstant(due), REACTIVE_INSPECTION_PRIORITY, patrol.taskId(), "frontier.route_patrol.progress", 1); }
    private static RoutePatrol selectIngressCapablePatrol(FrontierWorldState state, StrategicTask task, Settlement settlement,
                                                           List<ResidentProfile> candidates) {
        for (ResidentProfile leader : candidates) for (ResidentProfile scout : candidates) {
            if (leader.id().equals(scout.id())) continue;
            RouteUnitManifest unit = RouteUnitManifest.patrol(task.id(), leader.id(), List.of(scout.id()));
            TraversalTopology inspection = RoutePatrol.inspectionTopology(state, task, settlement);
            if (!PatrolAssemblyCorridor.canCompile(state, settlement, unit, inspection)) continue;
            RoutePatrol patrol = RoutePatrol.planned(state, task, settlement, unit);
            if (patrol.assemblyCanReachFormation()) return patrol;
        }
        return null;
    }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static StrategicTask task(FrontierWorldState state, SubjectId id, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(id);
        if (task == null || task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE || task.status() != status) throw new IllegalStateException("route patrol has no matching strategic task");
        return task;
    }
}
