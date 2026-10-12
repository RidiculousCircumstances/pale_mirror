package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Patrol-owned rejoin and geometry knowledge; the common departure alone records actual poses. */
public final class PatrolSpatialContinuation {
    private PatrolSpatialContinuation() { }
    static ActorActivityBodyCheckpoint.Acknowledgement acknowledge(ActorActivityBodyCheckpoint.Request request, RoutePatrol patrol) {
        var state = request.expectedState();
        var current = FrontierRoutePatrolSceneSupport.bodies(patrol);
        boolean divergent = patrol.memberIds().stream().anyMatch(actor -> !current.get(actor).equals(
                actor.equals(request.execution().actorId()) ? request.observedPosition() : state.actorLocations().get(actor).body()));
        if (!divergent) return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
        PatrolAssembly assembly = patrol.assembly(); PatrolTravel travel = patrol.travel();
        if (patrol.status() == RoutePatrolStatus.ASSEMBLING) {
            var members = new LinkedHashMap<>(assembly.members());
            members.replaceAll((actor, member) -> {
                var observed = actor.equals(request.execution().actorId()) ? request.observedPosition().supportingSurface()
                        : state.actorLocations().get(actor).supportingSurface();
                if (member.currentSurface().equals(observed)) return member;
                return member.observedAt(observed);
            });
            assembly = new PatrolAssembly(members);
        } else {
            var members = new LinkedHashMap<>(travel.members());
            members.replaceAll((actor, member) -> {
                var observed = actor.equals(request.execution().actorId()) ? request.observedPosition().supportingSurface()
                        : state.actorLocations().get(actor).supportingSurface();
                if (member.currentSurface().equals(observed) && (member.spatial().pending() || member.arrived())) return member;
                return member.observedAt(observed);
            });
            travel = new PatrolTravel(travel.leaderId(), travel.leaderRoute(), members);
        }
        var next = patrol.withSpatialContinuation(assembly, travel);
        return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin()
                .strategicPlans(state.strategicPlans().replacePatrol(next)));
    }
    public static boolean planningRequired(RoutePatrol patrol) {
        return patrol.status() == RoutePatrolStatus.ASSEMBLING ? patrol.assembly().planningRequired()
                : patrol.status() == RoutePatrolStatus.EN_ROUTE && patrol.travel().planningRequired();
    }

    /** One producer-owned, all-or-nothing selection. Pending knowledge never becomes a blocked patrol. */
    public static RoutePatrolSpatialPlanned plan(FrontierWorldState state, RoutePatrol patrol) {
        var knowledge = view(state, patrol);
        var executions = RoutePatrolExecutionAuthority.current(state, patrol);
        Map<SubjectId, TraversalRejoin> selected = new LinkedHashMap<>();
        for (var actor : patrol.memberIds().stream().sorted().toList()) {
            var spatial = spatial(patrol, actor);
            if (spatial.waitingOrigin().isEmpty()) continue;
            var order = order(patrol, actor, executions.requireMember(actor).generation());
            selected.put(actor, StationApproachNavigation.plan(knowledge, spatial, order).orElseThrow());
        }
        return new RoutePatrolSpatialPlanned(patrol.taskId(), PatrolFormationStep.capture(patrol), selected, executions);
    }

    /** Pure replay: validate the exact selected paths, not planner readiness or alternative ranking. */
    public static RoutePatrol adopt(FrontierWorldState state, RoutePatrol patrol, RoutePatrolSpatialPlanned planned) {
        planned.predecessor().requireCurrent(patrol);
        RoutePatrolExecutionAuthority.requireCurrent(state, patrol, planned.executions());
        var waiting = patrol.memberIds().stream().filter(actor -> spatial(patrol, actor).waitingOrigin().isPresent())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!waiting.equals(planned.approaches().keySet()))
            throw new IllegalArgumentException("patrol selection must cover exactly its waiting cohort");
        var knowledge = view(state, patrol);
        planned.approaches().forEach((actor, selected) -> StationApproachNavigation.requireSelected(knowledge,
                spatial(patrol, actor), order(patrol, actor, planned.executions().requireMember(actor).generation()), selected));
        if (patrol.status() == RoutePatrolStatus.ASSEMBLING) {
            var members = new LinkedHashMap<>(patrol.assembly().members());
            planned.approaches().forEach((actor, path) -> members.put(actor, members.get(actor).withPlannedApproach(path)));
            return patrol.withSpatialContinuation(new PatrolAssembly(members), patrol.travel());
        }
        var members = new LinkedHashMap<>(patrol.travel().members());
        planned.approaches().forEach((actor, path) -> members.put(actor, members.get(actor).withPlannedApproach(path)));
        return patrol.withSpatialContinuation(patrol.assembly(), new PatrolTravel(patrol.travel().leaderId(), patrol.travel().leaderRoute(), members));
    }

    private static StationApproachState spatial(RoutePatrol patrol, SubjectId actor) {
        return patrol.status() == RoutePatrolStatus.ASSEMBLING ? patrol.assembly().members().get(actor).spatial()
                : patrol.travel().members().get(actor).spatial();
    }
    private static MovementOrder order(RoutePatrol patrol, SubjectId actor, long generation) {
        var target = patrol.status() == RoutePatrolStatus.ASSEMBLING ? patrol.assembly().members().get(actor).checkpointSurface()
                : patrol.travel().members().get(actor).checkpointSurface();
        return new MovementOrder(patrol.taskId(), actor, generation, spatial(patrol, actor).revision(), List.of(target),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    private static KnownPedestrianRouteKnowledge view(FrontierWorldState state, RoutePatrol patrol) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), patrol.settlementId());
        var hall = settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL)
                .reduce((left, right) -> { throw new IllegalArgumentException("patrol has duplicate Hall ports"); }).orElseThrow();
        return KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(),
                List.of(new KnownPedestrianRouteKnowledge.Passage(hall, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
    public static boolean openFormation(FrontierWorldState state, RoutePatrol current, RoutePatrol next) {
        var knowledge = view(state, current);
        var before = FrontierRoutePatrolSceneSupport.bodies(current);
        var after = FrontierRoutePatrolSceneSupport.bodies(next);
        return current.memberIds().stream().allMatch(actor -> knowledge.traversable(
                List.of(before.get(actor).supportingSurface(), after.get(actor).supportingSurface())));
    }
}
