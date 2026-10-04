package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.LinkedHashMap;
import java.util.List;

/** Patrol-owned rejoin and geometry knowledge; the common departure alone records actual poses. */
public final class PatrolSpatialContinuation {
    private PatrolSpatialContinuation() { }
    static ActorActivityBodyCheckpoint.Acknowledgement acknowledge(ActorActivityBodyCheckpoint.Request request, RoutePatrol patrol) {
        var state = request.expectedState();
        var current = FrontierRoutePatrolSceneSupport.bodies(patrol);
        boolean divergent = patrol.memberIds().stream().anyMatch(actor -> !current.get(actor).equals(
                actor.equals(request.execution().actorId()) ? request.observedPosition() : state.actorLocations().get(actor).body()));
        if (!divergent) return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
        var knowledge = view(state, patrol);
        PatrolAssembly assembly = patrol.assembly(); PatrolTravel travel = patrol.travel();
        if (patrol.status() == RoutePatrolStatus.ASSEMBLING) {
            var members = new LinkedHashMap<>(assembly.members());
            members.replaceAll((actor, member) -> {
                var observed = actor.equals(request.execution().actorId()) ? request.observedPosition().supportingSurface()
                        : state.actorLocations().get(actor).supportingSurface();
                if (member.currentSurface().equals(observed)) return member;
                return member.withRejoin(approach(knowledge, patrol, actor, observed, member.checkpointSurface(), member.routeRevision()));
            });
            assembly = new PatrolAssembly(members);
        } else {
            var members = new LinkedHashMap<>(travel.members());
            members.replaceAll((actor, member) -> {
                var observed = actor.equals(request.execution().actorId()) ? request.observedPosition().supportingSurface()
                        : state.actorLocations().get(actor).supportingSurface();
                if (member.currentSurface().equals(observed) && (member.rejoin().isPresent() || member.arrived())) return member;
                return member.withRejoin(approach(knowledge, patrol, actor, observed, member.checkpointSurface(), member.routeRevision()));
            });
            travel = new PatrolTravel(travel.leaderId(), travel.leaderRoute(), members);
        }
        var next = patrol.withSpatialContinuation(assembly, travel);
        return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin()
                .strategicPlans(state.strategicPlans().replacePatrol(next)));
    }
    private static TraversalRejoin approach(KnownPedestrianRouteKnowledge knowledge, RoutePatrol patrol, SubjectId actor,
                                             SurfaceAnchor observed, SurfaceAnchor target, long revision) {
        var order = new MovementOrder(patrol.taskId(), actor, 1L, Math.incrementExact(revision), List.of(target),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        var path = knowledge.path(observed, order);
        if (path.isEmpty()) throw new IllegalArgumentException("patrol departure has no bounded known rejoin to its retained checkpoint");
        return new TraversalRejoin(path, 0);
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
