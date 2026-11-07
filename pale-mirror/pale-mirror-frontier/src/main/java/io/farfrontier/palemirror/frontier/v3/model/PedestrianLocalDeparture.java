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
        return !FacilityPedestrianContacts.of(state.bootstrap()).noParking().contains(surface.support())
                && KnownPedestrianRouteKnowledge.forFrontier(state).traversable(List.of(surface));
    }

    /** A private-station escape precedes an outdoor approach; it is not an unauthorized re-entry. */
    public static java.util.Optional<List<SurfaceAnchor>> publicContinuation(FrontierWorldState state, List<SurfaceAnchor> route) {
        var start = route.getFirst();
        if (publicPosition(state, start)) return java.util.Optional.of(route);
        for (int index = 0; index < route.size(); index++)
            if (publicPosition(state, route.get(index))) return java.util.Optional.of(route.subList(index, route.size()));
        return java.util.Optional.empty();
    }

    private static List<KnownPedestrianRouteKnowledge.SettlementPassage> exits(FrontierWorldState state, SurfaceAnchor start) {
        return state.bootstrap().settlements().stream()
                .flatMap(settlement -> settlement.structures().stream()
                    .filter(facility -> FrontierTraversalPlan.facilityPorts(facility).stream().anyMatch(port ->
                            port.ingressSurfaces().contains(start) || port.stations().contains(start)
                            || FrontierGrayboxPlan.publicAccessSurfaces(facility).contains(start)))
                    .map(facility -> new KnownPedestrianRouteKnowledge.SettlementPassage(settlement.id(),
                            new KnownPedestrianRouteKnowledge.Passage(facility,
                                    KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)))).toList();
    }
}
