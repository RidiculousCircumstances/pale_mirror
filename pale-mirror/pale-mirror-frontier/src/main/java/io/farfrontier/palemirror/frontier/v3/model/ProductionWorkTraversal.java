package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Retains a production route computed by the same current geometry and navigator as other work. */
public final class ProductionWorkTraversal {
    private ProductionWorkTraversal() {}

    public static TraversalTopology compile(
            FrontierWorldState state,
            SettlementStructure workshop,
            SubjectId workerId,
            ActorLocation worker,
            SubjectId jobId) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(workshop);
        Objects.requireNonNull(workerId);
        Objects.requireNonNull(worker);
        Objects.requireNonNull(jobId);
        var port = SettlementWorkshopServicePort.forWorkshop(workshop);
        var knowledge =
                KnownPedestrianRouteKnowledge.forSettlement(
                                state,
                                workshop.settlementId(),
                                List.of(
                                        new KnownPedestrianRouteKnowledge.Passage(
                                                workshop,
                                                KnownPedestrianRouteKnowledge.Passage.Reach
                                                        .STATIONS)))
                        .forActor(state, workerId);
        var corridor = new ArrayList<SurfaceAnchor>();
        var start = worker.supportingSurface();
        corridor.add(start);
        for (var target : List.of(port.inputStation(), port.workStation())) {
            var order =
                    new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(
                            jobId,
                            workerId,
                            0,
                            1,
                            List.of(target),
                            TraversalCapability.PEDESTRIAN,
                            io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder
                                    .ArrivalPolicy.EXACT_STATION);
            var leg = knowledge.path(start, order);
            corridor.addAll(leg.subList(1, leg.size()));
            start = target;
        }
        return TraversalTopology.corridor(
                new TraversalTopologyId(
                        "topology:production-work-" + jobId.value().replace(':', '-')),
                revision(corridor),
                workshop.id(),
                TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN),
                corridor);
    }

    private static long revision(List<SurfaceAnchor> surfaces) {
        long hash = 0xcbf29ce484222325L;
        for (SurfaceAnchor surface : surfaces) {
            hash = (hash ^ Integer.toUnsignedLong(surface.x())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.y())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.z())) * 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }
}
