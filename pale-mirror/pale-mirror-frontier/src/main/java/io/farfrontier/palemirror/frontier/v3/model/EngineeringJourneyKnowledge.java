package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy;
import java.util.HashSet;
import java.util.List;

/** Engineering declares its depot passage and crew reservations; shared knowledge owns geometry. */
public final class EngineeringJourneyKnowledge {
    private EngineeringJourneyKnowledge() { }
    static KnownPedestrianRouteKnowledge view(FrontierWorldState state, EngineeringWorkOrder owner) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), owner.settlementId());
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .reduce((left, right) -> { throw new IllegalArgumentException("engineering has duplicate depot facilities"); })
                .orElseThrow(() -> new IllegalArgumentException("engineering journey has no declared depot"));
        return KnownPedestrianRouteKnowledge.forSettlement(state, owner.settlementId(),
                List.of(new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
    static List<SurfaceAnchor> rejoin(FrontierWorldState state, EngineeringWorkOrder owner, SubjectId actor, SurfaceAnchor observed) {
        var assembly = owner.assembly().orElseThrow();
        var member = assembly.members().get(actor);
        var target = new SurfaceAnchor(member.corridor().get(Math.min(member.cursor() + 1, member.corridor().size() - 1)));
        var reserved = new HashSet<SurfaceAnchor>();
        assembly.members().forEach((id, other) -> { if (!id.equals(actor)) reserved.add(other.currentSurface()); });
        var order = new MovementOrder(owner.id(), actor, 1L, Math.incrementExact(member.routeRevision()),
                List.of(target), TraversalCapability.PEDESTRIAN, ArrivalPolicy.EXACT_STATION);
        var path = view(state, owner).pathAvoiding(observed, order, reserved);
        if (path.isEmpty()) throw new IllegalArgumentException("engineering departure has no known bounded rejoin to its retained checkpoint");
        return path;
    }
    public static boolean openEdge(FrontierWorldState state, EngineeringWorkOrder owner, SubjectId actor) {
        var member = owner.assembly().orElseThrow().members().get(actor);
        return view(state, owner).traversable(List.of(member.currentSurface(), member.nextSurface()));
    }
}
