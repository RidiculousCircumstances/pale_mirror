package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Service owns its unfinished station; shared geometry owns the bounded approach. */
public final class SettlementServiceJourneyKnowledge {
    private SettlementServiceJourneyKnowledge() { }
    public static SurfaceAnchor target(SettlementServiceWork work) {
        return target(work.phase(), work.inputTraversal(), work.inputTraversalCursor(),
                work.workTraversal(), work.workTraversalCursor());
    }
    static SurfaceAnchor target(SettlementServiceWorkPhase phase, TraversalTopology input, int inputCursor,
                                TraversalTopology work, int workCursor) {
        return switch (phase) {
            case PREPARED, APPROACH_INPUT -> next(input, inputCursor);
            case INPUT_ISSUE_PENDING -> input.linearCorridorSurfaces().getLast();
            case APPROACH_WORK -> next(work, workCursor);
            case WORKING, EFFECT_READY, UNKNOWN_AFTER_RESTART, BLOCKED, COMPLETED -> work.linearCorridorSurfaces().get(workCursor);
        };
    }
    private static SurfaceAnchor next(TraversalTopology topology, int cursor) {
        var route = topology.linearCorridorSurfaces();
        return route.get(Math.min(cursor + 1, route.size() - 1));
    }
    public static KnownPedestrianRouteKnowledge view(FrontierWorldState state, SettlementServiceWork work) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), work.settlementId());
        var depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("service approach lost its depot"));
        return KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(),
                List.of(new KnownPedestrianRouteKnowledge.Passage(depot,
                        KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
    public static StationApproachState checkpoint(FrontierWorldState state, SettlementServiceWork work, SurfaceAnchor observed) {
        var order = new MovementOrder(work.id(), work.workerId(), 1L, Math.incrementExact(work.spatial().revision()),
                List.of(target(work)), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        try {
            var path = view(state, work).path(observed, order);
            return new StationApproachState(order.goalRevision(), Optional.of(new TraversalRejoin(path, 0)), Optional.empty());
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return new StationApproachState(order.goalRevision(), Optional.empty(), Optional.of(observed));
        }
    }
    /** Reopening presentation can precede body unload; preserve its independently inspected pose. */
    public static FrontierWorldState atScopeAdmission(FrontierWorldState state, SettlementServiceWork work) {
        SettlementServiceExecutionAuthority.current(state, work);
        var actor = state.actorLocations().get(work.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("service scope admission lost its living worker");
        if (FrontierSettlementServiceWorkSceneSupport.currentSurface(work).equals(actor.supportingSurface())) return state;
        var works = new LinkedHashMap<>(state.serviceWorks());
        works.put(work.id(), work.withSpatial(checkpoint(state, work, actor.supportingSurface())));
        return state.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(works));
    }

    static ActorActivityBodyCheckpoint.Acknowledgement acknowledge(ActorActivityBodyCheckpoint.Request request) {
        var state = request.expectedState();
        var work = state.serviceWorks().get(request.execution().activityOwnerId());
        if (request.execution().activityKind() != ActorActivityKind.SETTLEMENT_SERVICE || work == null
                || !work.workerId().equals(request.execution().actorId()))
            throw new IllegalArgumentException("service departure lost its exact worker and work");
        var update = FrontierWorldStateUpdate.begin();
        if (!FrontierSettlementServiceWorkSceneSupport.sceneEligible(work.phase()))
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, update);
        if (FrontierSettlementServiceWorkSceneSupport.currentSurface(work).equals(request.observedPosition().supportingSurface()))
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, update);
        var works = new LinkedHashMap<>(state.serviceWorks());
        works.put(work.id(), work.withSpatial(checkpoint(state, work, request.observedPosition().supportingSurface())));
        return new ActorActivityBodyCheckpoint.Acknowledgement(request, update.serviceWorks(works));
    }
}
