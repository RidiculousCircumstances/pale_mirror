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
        requireCurrentPlan(state, patrol);
        if (state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && FrontierSceneBehaviors.isRoutePatrol(lease)
                && FrontierSceneBehaviors.routePatrol(lease).taskId().equals(patrol.taskId()))) return List.of();
        StrategicTask task = task(state, patrol.taskId(), StrategicTaskStatus.ACTIVE);
        RoutePatrol current = patrol;
        List<ProposedEvent> events = new ArrayList<>();
        // A scheduled COLD turn is one operation boundary, never a hidden batch of resident
        // teleports.  HOT supplies the same edge through observed arrival.
        for (int advance = 0; advance < 1; advance++) {
            if (current.memberIds().stream().anyMatch(member -> state.actorLocations().get(member).condition().status() != ActorLifeStatus.ALIVE)) {
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
            if (current.active()) {
                RoutePatrol next = current.advanceFormation();
                // COLD owns the same retained formation edge; no actor/body coordinate is selected here.
                events.add(new ProposedEvent(current.settlementId(), new RoutePatrolFormationAdvanced(current.taskId())));
                current = next;
                if (current.status() == RoutePatrolStatus.ROUTE_CLEAR) { events.add(transition(task, StrategicTaskStatus.COMPLETED)); return List.copyOf(events); }
                break;
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
        if (!patrol.tacticalPlan().equals(TacticalPlan.routePatrol(task, patrol.unit(), patrol.route()))) {
            throw new IllegalArgumentException("route patrol start has a substituted tactical plan");
        }
        for (var entry : patrol.assembly().bodies().entrySet()) if (!state.actorLocations().get(entry.getKey()).body().equals(entry.getValue())) {
            throw new IllegalArgumentException("route patrol ingress must begin at each exact current resident body");
        }
        return state.withStrategicPlans(state.strategicPlans().startPatrol(patrol));
    }

    /** COLD counterpart of one observed HOT formation edge: update every named body atomically. */
    public static FrontierWorldState reduceFormationAdvanced(FrontierWorldState state, SubjectId subject, RoutePatrolFormationAdvanced advanced) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(advanced.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId()) || !patrol.active()) {
            throw new IllegalArgumentException("route-patrol formation advance has foreign owner");
        }
        RoutePatrol next = patrol.advanceFormation();
        java.util.Map<SubjectId, ActorLocation> locations = new java.util.LinkedHashMap<>(state.actorLocations());
        for (var entry : FrontierRoutePatrolSceneSupport.bodies(patrol).entrySet()) {
            ActorLocation current = locations.get(entry.getKey());
            if (current == null || !current.body().equals(entry.getValue())) {
                throw new IllegalArgumentException("route-patrol formation advance has a stale resident body");
            }
            locations.put(entry.getKey(), current.withBody(FrontierRoutePatrolSceneSupport.bodies(next).get(entry.getKey())));
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations)
                .strategicPlans(state.strategicPlans().advancePatrolFormation(advanced.taskId())));
    }

    static FrontierWorldState reduceObstruction(FrontierWorldState state, SubjectId subject, RoutePatrolObstructionConfirmed confirmed) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(confirmed.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId()) || !state.physicalDeltas().containsKey(confirmed.position())) {
            throw new IllegalArgumentException("route patrol obstruction lacks physical evidence");
        }
        requireCurrentPlan(state, patrol);
        return state.withStrategicPlans(state.strategicPlans().confirmPatrolObstruction(confirmed.taskId(), confirmed.position()));
    }

    /** One named patrol member loss is terminal evidence for this exact roster; no substitute may continue it. */
    public static FrontierWorldState reduceFailed(FrontierWorldState state, SubjectId subject, RoutePatrolFailed failed) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(failed.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId()) || patrol.memberIds().stream()
                .allMatch(member -> state.actorLocations().get(member).condition().status() == ActorLifeStatus.ALIVE)) {
            throw new IllegalArgumentException("route patrol failure lacks a dead guard");
        }
        requireCurrentPlan(state, patrol);
        return state.withStrategicPlans(state.strategicPlans().failPatrol(failed.taskId()));
    }

    static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, RoutePatrolBlocked blocked) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(blocked.taskId());
        if (patrol == null || !subject.equals(patrol.settlementId()) || !patrol.active()) throw new IllegalArgumentException("route patrol block has a foreign owner");
        requireCurrentPlan(state, patrol);
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
        if (task == null || task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE || task.status() != status
                || !state.strategicPlans().currentDecisionAuthority(task.authorityId(), task.authorityEpoch())) {
            throw new IllegalStateException("route patrol has no current matching strategic task");
        }
        return task;
    }
    private static void requireCurrentPlan(FrontierWorldState state, RoutePatrol patrol) {
        if (!patrol.tacticalPlan().currentFor(state.strategicPlans())) {
            throw new IllegalStateException("route patrol has stale tactical authority");
        }
    }
}
