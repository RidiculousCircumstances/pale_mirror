package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Geometry for leaving an occupied declared port, independent of profession, task and home. */
public final class PedestrianLocalDeparture {
    private PedestrianLocalDeparture() { }

    public static KnownPedestrianRouteKnowledge departure(FrontierWorldState state, SurfaceAnchor start) {
        return departure(state, start, List.of());
    }

    /** Planning and receipt validation share actual exit and declared continuation permissions. */
    public static KnownPedestrianRouteKnowledge departure(FrontierWorldState state, SurfaceAnchor start,
            List<KnownPedestrianRouteKnowledge.SettlementPassage> continuation) {
        var passages = new java.util.ArrayList<>(exits(state, start));
        passages.addAll(continuation);
        var declarations = passages.stream().distinct().toList();
        return declarations.isEmpty() ? KnownPedestrianRouteKnowledge.forFrontier(state)
                : KnownPedestrianRouteKnowledge.forJourney(state, declarations);
    }

    public static boolean publicPosition(FrontierWorldState state, SurfaceAnchor surface) {
        return KnownPedestrianRouteKnowledge.forFrontier(state).traversable(List.of(surface));
    }

    /** A private-station escape precedes an outdoor approach; it is not an unauthorized re-entry. */
    public static List<SurfaceAnchor> publicContinuation(FrontierWorldState state, List<SurfaceAnchor> route) {
        var start = route.getFirst();
        if (publicPosition(state, start)) return route;
        for (int index = 0; index < route.size(); index++)
            if (publicPosition(state, route.get(index))) return route.subList(index, route.size());
        throw new IllegalArgumentException("private departure route never reaches public geometry");
    }

    /** Any subsequent activity can leave a declared private station from actual position. */
    public static List<SurfaceAnchor> exitToPublic(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor, SurfaceAnchor start) {
        if (publicPosition(state, start)) return List.of(start);
        var knowledge = departure(state, start);
        List<SurfaceAnchor> best = null;
        for (var exit : exits(state, start)) {
            var target = FrontierTraversalPlan.facilityPort(exit.passage().facility()).orElseThrow()
                    .exteriorApproach().getFirst();
            if (!publicPosition(state, target)) continue;
            var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(actor, actor, 0, 1,
                    List.of(target), TraversalCapability.PEDESTRIAN,
                    io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
            try {
                var route = knowledge.path(start, order);
                if (best == null || route.size() < best.size()) best = route;
            } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                // An actual blocked exit stays blocked; no fabricated station or terrain.
            }
        }
        if (best == null) throw new io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable(
                "actual facility position has no declared public exit");
        return best;
    }

    private static List<KnownPedestrianRouteKnowledge.SettlementPassage> exits(FrontierWorldState state, SurfaceAnchor start) {
        return state.bootstrap().settlements().stream()
                .flatMap(settlement -> settlement.structures().stream()
                    .filter(facility -> FrontierTraversalPlan.facilityPort(facility).filter(port ->
                            port.ingressSurfaces().contains(start) || port.stations().contains(start)
                            || FrontierGrayboxPlan.publicAccessSurfaces(facility).contains(start)).isPresent())
                    .map(facility -> new KnownPedestrianRouteKnowledge.SettlementPassage(settlement.id(),
                            new KnownPedestrianRouteKnowledge.Passage(facility,
                                    KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)))).toList();
    }
}
