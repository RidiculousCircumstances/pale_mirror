package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Logistics owns formation goals/continuations; shared geometry owns path availability. */
public final class OperationTravelContinuation {
    private OperationTravelContinuation() { }
    /** Scope reopening can precede physical unload; record the actual origin without moving it. */
    public static FrontierWorldState atScopeAdmission(FrontierWorldState state, SceneLease scope) {
        var cause = FrontierSceneBehaviors.logistics(scope);
        var operation = state.operations().get(cause.operationId());
        if (operation == null) throw new IllegalArgumentException("logistics admission lost its declared operation");
        OperationExecutionAuthority.logisticsCurrent(state, operation);
        if (operation.activeTravel().isEmpty()) return state;
        var travel = operation.activeTravel().orElseThrow();
        for (var actor : operation.participantIds()) {
            var location = state.actorLocations().get(actor);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("logistics admission lost its living crew");
            if (!travel.memberCheckpoint(actor).equals(location.body()))
                travel = travel.checkpointMember(actor, checkpoint(state, operation.withTravel(travel), actor, location.supportingSurface()));
        }
        if (travel.equals(operation.activeTravel().orElseThrow())) return state;
        var operations = new LinkedHashMap<>(state.operations()); operations.put(operation.id(), operation.withTravel(travel));
        return state.withChanges(FrontierWorldStateUpdate.begin().operations(operations));
    }

    public static StationApproachState checkpoint(FrontierWorldState state, RouteOperation operation,
                                                   SubjectId actor, SurfaceAnchor observed) {
        var travel = operation.activeTravel().orElseThrow();
        var prior = travel.approaches().get(actor);
        long revision = prior == null ? 1L : Math.incrementExact(prior.revision());
        var goal = travel.nextFormationBody(actor).supportingSurface();
        var order = new MovementOrder(operation.id(), actor, travel.cursor(), revision, List.of(goal),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        try {
            return new StationApproachState(revision, Optional.of(new TraversalRejoin(
                    KnownPedestrianRouteKnowledge.forFrontier(state).path(observed, order), 0)), Optional.empty());
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return new StationApproachState(revision, Optional.empty(), Optional.of(observed));
        }
    }

    /** One bounded background approach turn. It cannot move the route cursor or cargo. */
    public static OperationTravel coldApproached(FrontierWorldState state, RouteOperation operation) {
        var travel = operation.activeTravel().orElseThrow();
        var next = new LinkedHashMap<>(travel.approaches());
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        for (var entry : travel.approaches().entrySet()) {
            var actor = entry.getKey(); var spatial = entry.getValue();
            if (spatial.approach().isEmpty() || !knowledge.traversable(spatial.approach().orElseThrow().path()
                    .subList(spatial.approach().orElseThrow().cursor(), spatial.approach().orElseThrow().path().size()))) {
                var rebuilt = checkpoint(state, operation, actor, state.actorLocations().get(actor).supportingSurface());
                // Unknown knowledge is a hold, not a repeated WAL revision for unchanged waiting.
                if (rebuilt.waitingOrigin().equals(spatial.waitingOrigin()) && rebuilt.approach().equals(spatial.approach())) continue;
                spatial = rebuilt;
            }
            if (spatial.approach().isPresent()) {
                var approach = spatial.approach().orElseThrow();
                if (!approach.arrived()) spatial = new StationApproachState(Math.incrementExact(spatial.revision()),
                        Optional.of(approach.advance(approach.nextCursor(OperationTravel.MAX_COLD_ADVANCE), OperationTravel.MAX_COLD_ADVANCE)), Optional.empty());
            }
            next.put(actor, spatial);
        }
        return next.equals(travel.approaches()) ? travel : travel.withApproaches(next);
    }

    public static boolean approachesReady(OperationTravel travel) {
        return travel.approaches().values().stream().allMatch(spatial -> spatial.approach().isPresent()
                && spatial.approach().orElseThrow().arrived());
    }

    /** Validate every intermediate retained member step, not only the final endpoint. */
    public static boolean coldSegmentAvailable(FrontierWorldState state, OperationTravel prior, OperationTravel next) {
        if (!prior.approaches().isEmpty() && (!approachesReady(prior) || !next.isExactHotAdvanceFrom(prior))) return false;
        var ground = KnownPedestrianGround.forFrontier(state);
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        for (int cursor = prior.cursor() + 1; cursor <= next.cursor(); cursor++) {
            var from = prior.currentPosition(); var to = prior.corridor().get(cursor);
            for (var body : prior.formation().values()) {
                var surface = body.offset(to.x() - from.x(), to.y() - from.y(), to.z() - from.z()).supportingSurface();
                if (!surface.equals(ground.at(surface.x(), surface.z())) || !knowledge.traversable(List.of(surface))) return false;
            }
        }
        return true;
    }
}
