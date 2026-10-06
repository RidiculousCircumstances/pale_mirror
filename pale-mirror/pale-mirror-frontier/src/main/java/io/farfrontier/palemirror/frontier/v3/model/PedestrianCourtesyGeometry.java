package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Geometry for leaving an occupied declared port, independent of profession, task and home. */
public final class PedestrianCourtesyGeometry {
    private PedestrianCourtesyGeometry() { }

    public static KnownPedestrianRouteKnowledge departure(FrontierWorldState state, SurfaceAnchor start) {
        List<KnownPedestrianRouteKnowledge.SettlementPassage> exits = state.bootstrap().settlements().stream()
                .flatMap(settlement -> settlement.structures().stream()
                    .filter(facility -> FrontierTraversalPlan.facilityPort(facility).filter(port ->
                            port.ingressSurfaces().contains(start) || port.stations().contains(start)
                            || FrontierGrayboxPlan.publicAccessSurfaces(facility).contains(start)).isPresent())
                    .map(facility -> new KnownPedestrianRouteKnowledge.SettlementPassage(settlement.id(),
                            new KnownPedestrianRouteKnowledge.Passage(facility,
                                    KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)))).toList();
        // Only the current occupied facility is opened for escape; unrelated interiors stay closed.
        return exits.isEmpty() ? KnownPedestrianRouteKnowledge.forFrontier(state)
                : KnownPedestrianRouteKnowledge.forJourney(state, exits);
    }
}
