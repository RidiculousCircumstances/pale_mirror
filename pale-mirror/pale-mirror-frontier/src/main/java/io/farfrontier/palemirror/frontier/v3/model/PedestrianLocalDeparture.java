package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Geometry for leaving an occupied declared port, independent of profession, task and home. */
public final class PedestrianLocalDeparture {
    private static final ImmutableInputView<java.util.Map<SurfaceAnchor, List<KnownPedestrianRouteKnowledge.SettlementPassage>>> EXITS =
            new ImmutableInputView<>();
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

    static List<KnownPedestrianRouteKnowledge.SettlementPassage> exits(FrontierWorldState state, SurfaceAnchor start) {
        // Only immutable declarations are indexed. Current collision, custody and
        // traversal permissions remain the journey knowledge's responsibility.
        return EXITS.get(List.of(state.bootstrap()), () -> {
            var indexed = new java.util.LinkedHashMap<SurfaceAnchor, List<KnownPedestrianRouteKnowledge.SettlementPassage>>();
            for (var settlement : state.bootstrap().settlements()) for (var facility : settlement.structures()) {
                var ports = FrontierTraversalPlan.facilityPorts(facility);
                if (ports.isEmpty()) continue;
                var contacts = new java.util.LinkedHashSet<>(FrontierGrayboxPlan.publicAccessSurfaces(facility));
                for (var port : ports) { contacts.addAll(port.ingressSurfaces()); contacts.addAll(port.stations()); }
                var passage = new KnownPedestrianRouteKnowledge.SettlementPassage(settlement.id(),
                        new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS));
                for (var contact : contacts) indexed.computeIfAbsent(contact, ignored -> new java.util.ArrayList<>()).add(passage);
            }
            indexed.replaceAll((contact, passages) -> List.copyOf(passages));
            return java.util.Map.copyOf(indexed);
        }).getOrDefault(start, List.of());
    }
}
