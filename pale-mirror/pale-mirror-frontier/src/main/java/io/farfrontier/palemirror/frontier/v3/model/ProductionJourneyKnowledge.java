package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Production declares its station; the shared read-only geometry supplies the approach. */
public final class ProductionJourneyKnowledge {
    private ProductionJourneyKnowledge() { }
    public static SurfaceAnchor target(TraversalTopology topology, int cursor, ProductionWorkProgress progress) {
        var route = topology.linearCorridorSurfaces();
        boolean input = progress.stage() == ProductionWorkProgress.Stage.APPROACH && cursor == route.size() - 2;
        return route.get(input ? cursor : Math.min(cursor + 1, route.size() - 1));
    }
    public static SurfaceAnchor target(ProductionJob job) {
        return target(job.workTraversal(), job.traversalCursor(), job.workProgress());
    }
    public static KnownPedestrianRouteKnowledge view(FrontierWorldState state, ProductionJob job) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        var workshop = settlement.structures().stream().filter(value -> value.id().equals(job.facilityId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("production approach lost its declared facility"));
        return KnownPedestrianRouteKnowledge.forSettlement(state, job.settlementId(), List.of(
                new KnownPedestrianRouteKnowledge.Passage(workshop, KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
    }
    public static StationApproachState checkpoint(ProductionJob job, SurfaceAnchor observed) {
        return job.spatial().observedAt(observed);
    }
    public static MovementOrder order(ProductionJob job) {
        return new MovementOrder(job.id(), job.workerId(), 1L, job.spatial().revision(),
                List.of(target(job)), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public static Optional<TraversalRejoin> approach(FrontierWorldState state, ProductionJob job) {
        try {
            return StationApproachNavigation.plan(view(state, job), job.spatial(), order(job));
        } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
            if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                throw unavailable;
            return Optional.empty();
        }
    }
    /** Scope is presentation only: its return may occur while the same physical body is still held. */
    public static FrontierWorldState atScopeAdmission(FrontierWorldState state, ProductionJob job) {
        var execution = state.actorExecutions().current(ActorActivityKind.PRODUCTION).get(job.workerId());
        if (execution == null || !execution.activityOwnerId().equals(job.id()))
            throw new IllegalArgumentException("production scope admission lost its exact worker execution");
        if (job.bakeryWork().isPresent()) return state;
        var actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("production scope admission lost its living worker");
        var retained = job.spatial().current(job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()));
        if (retained.equals(actor.supportingSurface())) return state;
        var jobs = new LinkedHashMap<>(state.productionJobs());
        jobs.put(job.id(), job.withSpatial(checkpoint(job, actor.supportingSurface())));
        return state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs));
    }

    static ActorActivityBodyCheckpoint.Acknowledgement acknowledge(ActorActivityBodyCheckpoint.Request request) {
        var state = request.expectedState();
        var job = state.productionJobs().get(request.execution().activityOwnerId());
        if (request.execution().activityKind() != ActorActivityKind.PRODUCTION || job == null
                || !job.workerId().equals(request.execution().actorId()))
            throw new IllegalArgumentException("production departure lost its exact declared worker/job");
        var update = FrontierWorldStateUpdate.begin();
        if (job.bakeryWork().isPresent() || job.workProgress().terminalEffectEligible())
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, update);
        var current = job.spatial().current(job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()));
        if (current.equals(request.observedPosition().supportingSurface()))
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, update);
        var jobs = new LinkedHashMap<>(state.productionJobs());
        jobs.put(job.id(), job.withSpatial(checkpoint(job, request.observedPosition().supportingSurface())));
        return new ActorActivityBodyCheckpoint.Acknowledgement(request, update.productionJobs(jobs));
    }
}
