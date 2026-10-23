package io.farfrontier.palemirror.frontier.v3.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Cooperative known path to the current bakery handoff station; no private synchronous runtime planner. */
public final class StationWorkNavigation {
    private StationWorkNavigation() { }

    public static List<SurfaceAnchor> path(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "bakery navigation state");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("bakery movement has no living actor");
        return pathFrom(state, job, actor.supportingSurface());
    }

    /** HOT may start from the scene's last durable body rather than the dormant COLD body. */
    public static List<SurfaceAnchor> pathFrom(FrontierWorldState state, ProductionJob job, SurfaceAnchor start) {
        return pathFrom(state, job, start, false);
    }

    public static List<SurfaceAnchor> plannedPath(FrontierWorldState state, ProductionJob job) {
        var actor = Objects.requireNonNull(state.actorLocations().get(job.workerId()), "bakery actor");
        if (actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("bakery actor is not alive");
        return pathFrom(state, job, actor.supportingSurface(), false);
    }

    public static List<SurfaceAnchor> retainedPath(FrontierWorldState state, ProductionJob job) {
        return pathFrom(state, job, state.actorLocations().get(job.workerId()).supportingSurface(), true);
    }

    private static List<SurfaceAnchor> pathFrom(FrontierWorldState state, ProductionJob job, SurfaceAnchor start,
            boolean retainedOnly) {
        Objects.requireNonNull(state, "bakery navigation state");
        Objects.requireNonNull(start, "bakery navigation start");
        StationWorkState work = job.stationWork().orElseThrow(() -> new IllegalArgumentException("job has no bakery movement goal"));
        if (work.phase() == StationWorkState.Phase.DELIVERED) return retainedOnly
                ? KnownServiceExitNavigation.retainedClearancePathFrom(state, job.settlementId(),
                    StationWorkStorage.destination(job), job.id(), job.workerId(), work.phase().wireTag(), 1L, start)
                : KnownServiceExitNavigation.plannedClearancePathFrom(state, job.settlementId(),
                    StationWorkStorage.destination(job), job.id(), job.workerId(), work.phase().wireTag(), 1L, start);
        var goal = StationWorkGoal.current(state, job);
        // A completed arrival needs no new movement proof. Every nonempty move,
        // including workshop ingress/egress, uses the same current knowledge as replay.
        if (start.equals(goal.station())) return List.of(start);
        var knowledge = journeyKnowledge(state, job);
        return retainedOnly ? knowledge.retainedPath(start, goal.movementOrder())
                : knowledge.plannedPath(start, goal.movementOrder());
    }

    /** Bounded current-fact validation; replay does not calculate or rank routes. */
    public static void requireAcceptedDestination(FrontierWorldState state, ProductionJob job,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> receipt) {
        if (receipt.isPresent()) requireAcceptedRoute(state, job, receipt.orElseThrow());
        else if (!state.actorLocations().get(job.workerId()).supportingSurface().equals(StationWorkGoal.current(state, job).station()))
            throw new IllegalArgumentException("bakery admission lacks its route or observed destination");
    }

    /** Bounded current-fact validation; replay does not calculate or rank routes. */
    public static void requireAcceptedRoute(FrontierWorldState state, ProductionJob job,
            io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt receipt) {
        var route = receipt.route();
        if (!route.getFirst().equals(state.actorLocations().get(job.workerId()).supportingSurface()))
            throw new IllegalArgumentException("bakery route has a stale departure");
        var work = job.stationWork().orElseThrow();
        if (work.phase() == StationWorkState.Phase.DELIVERED) {
            var point = ServiceBoundaryComposition.declaration(state, StationWorkStorage.destination(job));
            if (!point.boundary().cleared(route.getLast().standingBody())
                    || ServiceDestinationClaims.forImmediateExit(state, job.workerId(), point.pointId()).contains(route.getLast()))
                throw new IllegalArgumentException("bakery route does not clear an available service boundary");
            ServiceBoundaryComposition.knowledge(state, point.identity()).forActor(state, job.workerId()).requireRoute(route);
            return;
        }
        if (!route.getLast().equals(StationWorkGoal.current(state, job).station()))
            throw new IllegalArgumentException("bakery route has a foreign phase goal");
        journeyKnowledge(state, job).requireRoute(route);
    }

    /** Bakery declares its destinations and access rights, never a private chain of traversable cells. */
    private static KnownPedestrianRouteKnowledge journeyKnowledge(FrontierWorldState state, ProductionJob job) {
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        var facility = home.structures().stream().filter(value -> value.id().equals(job.facilityId())).findFirst().orElseThrow();
        var passages = new ArrayList<>(ShipmentEndpointComposition.passages(state, StationWorkStorage.access(state, job).endpoint()));
        passages.add(new KnownPedestrianRouteKnowledge.SettlementPassage(job.settlementId(),
                new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
        return KnownPedestrianRouteKnowledge.forJourney(state, passages).forActor(state, job.workerId());
    }
}
