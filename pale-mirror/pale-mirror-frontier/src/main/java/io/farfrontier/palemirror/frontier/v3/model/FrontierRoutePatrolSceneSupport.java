package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Exact candidate, admission and observed-arrival boundary for class-D patrol movement.
 *
 * <p>The patrol retains route targets and formation checkpoints. ActorLocation owns actual
 * supported position in both providers; observed arrival updates it together with the patrol
 * checkpoint and scene recovery evidence. It never manufactures a generic guard goal.</p>
 */
public final class FrontierRoutePatrolSceneSupport {
    private FrontierRoutePatrolSceneSupport() { }

    /** Stable canonical inventory; loaded demand cannot select or hide a patrol. */
    public static List<Candidate> candidates(FrontierWorldState state) {
        return state.strategicPlans().routePatrols().values().stream()
                .sorted(java.util.Comparator.comparing(RoutePatrol::taskId))
                .map(patrol -> candidate(state, patrol)).flatMap(Optional::stream).toList();
    }

    public static Optional<Candidate> candidate(FrontierWorldState state, RoutePatrol patrol) {
        if (!patrol.active() || !patrol.tacticalPlan().currentFor(state.strategicPlans()) || hasScene(state, patrol.taskId())) return Optional.empty();
        StrategicTask task = state.strategicPlans().tasks().get(patrol.taskId());
        if (task == null || task.status() != StrategicTaskStatus.ACTIVE || !task.ownerId().equals(patrol.settlementId())) return Optional.empty();
        Map<SubjectId, BodyPosition> bodies = bodies(patrol);
        for (SubjectId member : patrol.memberIds()) {
            ActorLocation location = state.actorLocations().get(member);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE || !location.body().equals(bodies.get(member))) return Optional.empty();
        }
        SubjectId leader = patrol.guardId();
        return Optional.of(new Candidate(patrol.taskId(), patrol.settlementId(), leader,
                bodies.get(leader).supportingSurface().support(), Map.copyOf(bodies)));
    }

    public static RoutePatrol require(FrontierWorldState state, RoutePatrolSceneCause cause) {
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(cause.taskId());
        if (patrol == null) throw new IllegalArgumentException("route-patrol scene has no retained patrol");
        return patrol;
    }

    public static SubjectId owner(FrontierWorldState state, RoutePatrolSceneCause cause) {
        return require(state, cause).settlementId();
    }

    public static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        RoutePatrolSceneCause cause = FrontierSceneBehaviors.routePatrol(lease);
        RoutePatrol patrol = require(state, cause);
        Candidate candidate = candidate(state, patrol).orElseThrow(() -> new IllegalArgumentException("route-patrol scene has no exact ready formation"));
        Set<SubjectId> members = lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet());
        if (!lease.handoffPosition().equals(candidate.handoffPosition()) || !members.equals(new LinkedHashSet<>(patrol.memberIds()))
                || !lease.memberBodies(state.actorLocations()).equals(candidate.memberBodies())) {
            throw new IllegalArgumentException("route-patrol scene must retain its exact formation and cursor");
        }
    }

    public static SceneLease requireHotLease(FrontierWorldState state, RoutePatrol patrol, SceneLeaseId leaseId) {
        SceneLease lease = state.sceneLeases().get(leaseId);
        if (lease == null || lease.status() != SceneLeaseStatus.HOT || !FrontierSceneBehaviors.isRoutePatrol(lease)
                || !FrontierSceneBehaviors.routePatrol(lease).taskId().equals(patrol.taskId())
                || !lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet())
                .equals(new LinkedHashSet<>(patrol.memberIds())) || !lease.memberBodies(state.actorLocations()).equals(bodies(patrol))) {
            throw new IllegalArgumentException("route-patrol observation has no matching HOT formation lease");
        }
        return lease;
    }

    public static FrontierWorldState advanceFormationObserved(FrontierWorldState state, RoutePatrol current, SceneLeaseId leaseId,
                                                              Map<SubjectId, BodyPosition> observedBodies,
                                                              io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) {
        RoutePatrolExecutionAuthority.requireCurrent(state, current, executions);
        requireCurrentPlan(state, current); SceneLease lease = requireHotLease(state, current, leaseId);
        RoutePatrol next = current.advanceFormation(); Map<SubjectId, BodyPosition> expected = bodies(next);
        if (!expected.equals(observedBodies)) throw new IllegalArgumentException("route-patrol formation observation did not reach its retained edge");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        for (var entry : expected.entrySet()) {
            var actor = actors.get(entry.getKey());
            if (actor == null || !actor.body().equals(bodies(current).get(entry.getKey())))
                throw new IllegalArgumentException("patrol HOT observation has a stale canonical predecessor");
            actors.put(entry.getKey(), actor.withBody(entry.getValue()));
        }
        var update = FrontierWorldStateUpdate.begin().actorLocations(actors)
                .strategicPlans(state.strategicPlans().advancePatrolFormation(current.taskId()));
        if (!next.active()) update.actorExecutions(RoutePatrolExecutionAuthority.retired(state, current));
        return state.withChanges(update);
    }

    public static void validateRelease(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
        RoutePatrol patrol = require(state, FrontierSceneBehaviors.routePatrol(lease));
        if (!patrol.active()) {
            if (!patrol.memberIds().contains(actorId)) throw new IllegalArgumentException("terminal patrol release has a foreign member");
            // A stopped formation may be between checkpoints (including assembly).
            // Releasing its observed bodies must not teleport them onto the travel route.
            return;
        }
        requireCurrentPlan(state, patrol);
        BodyPosition expected = bodies(patrol).get(actorId);
        if (expected == null || !expected.equals(observed)) throw new IllegalArgumentException("route-patrol scene release diverged from its retained formation");
    }

    public static Map<SubjectId, BodyPosition> bodies(RoutePatrol patrol) {
        return switch (patrol.status()) {
            case ASSEMBLING -> patrol.assembly().bodies();
            case EN_ROUTE, ROUTE_CLEAR, OBSTRUCTION_CONFIRMED, BLOCKED, FAILED -> patrol.travel().bodies();
        };
    }

    private static boolean hasScene(FrontierWorldState state, SubjectId taskId) {
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isRoutePatrol)
                .anyMatch(lease -> FrontierSceneBehaviors.routePatrol(lease).taskId().equals(taskId));
    }

    private static void requireCurrentPlan(FrontierWorldState state, RoutePatrol patrol) {
        if (!patrol.tacticalPlan().currentFor(state.strategicPlans())) {
            throw new IllegalArgumentException("route-patrol scene has stale tactical authority");
        }
    }

    public record Candidate(SubjectId taskId, SubjectId settlementId, SubjectId leaderId, BlockPosition handoffPosition,
                            Map<SubjectId, BodyPosition> memberBodies) { }
}
