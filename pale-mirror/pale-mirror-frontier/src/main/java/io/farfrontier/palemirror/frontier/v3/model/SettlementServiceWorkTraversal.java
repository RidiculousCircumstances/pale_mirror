package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiles one resident-owned service corridor without consulting loaded Minecraft blocks. */
public final class SettlementServiceWorkTraversal {
    private SettlementServiceWorkTraversal() { }
    public static Plan compileDecontamination(FrontierWorldState state, Settlement settlement, SubjectId workerId, ActorLocation worker,
                                                 InfectionCell target, SubjectId workId, ResourceStorageAccess source) {
        Objects.requireNonNull(state, "service traversal state");
        Objects.requireNonNull(worker, "service traversal worker");
        Objects.requireNonNull(workerId, "service traversal worker id");
        if (!worker.equals(state.actorLocations().get(workerId)))
            throw new IllegalArgumentException("service traversal worker differs from its declared identity");
        Objects.requireNonNull(target, "service traversal infection target");
        Objects.requireNonNull(workId, "service traversal work id");
        Objects.requireNonNull(settlement, "service traversal settlement");
        if (!settlement.equals(FrontierWorldStateSupport.settlement(state.bootstrap(), settlement.id())))
            throw new IllegalArgumentException("service traversal requires its exact settlement");
        SurfaceAnchor start = worker.supportingSurface();
        if (!Objects.requireNonNull(source).settlementId().equals(settlement.id()))
            throw new IllegalArgumentException("service source belongs to another settlement");
        var knowledge = ResourceStorageNavigation.forEndpoint(state, source.endpoint());
        for (SurfaceAnchor inputStation : source.stations())
            for (SurfaceAnchor workStation : InfectionTreatmentWorksite.candidates(state.bootstrap(), target)) {
                try {
                    var inputOrder = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(
                            workId, workerId, 1L, 1L, List.of(inputStation), TraversalCapability.PEDESTRIAN,
                            io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
                    var workOrder = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(
                            workId, workerId, 2L, 1L, List.of(workStation), TraversalCapability.PEDESTRIAN,
                            io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
                    return new Plan(inputStation, workStation, topology("input", workId, knowledge.plannedPath(start, inputOrder)),
                            topology("work", workId, knowledge.plannedPath(inputStation, workOrder)));
                } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                    if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                        throw unavailable;
                }
            }

        throw new io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable(
                "service work has no bounded known route to infection station: " + target);
    }

    /** Check the chosen pair of legs against current facts, never rediscover stations or routes. */
    public static void requireSelected(FrontierWorldState state, SettlementServiceWork work) {
        var source = ResourceStorageAccess.at(state, work.inputSource().containerId());
        var actor = Objects.requireNonNull(state.actorLocations().get(work.workerId()), "service admission worker");
        if (!source.settlementId().equals(work.settlementId()) || !source.stations().contains(work.inputStation())
                || !(work.target() instanceof SettlementServiceTarget.Infection infection)
                || !InfectionTreatmentWorksite.candidates(state.bootstrap(), infection.cell()).contains(work.workStation())
                || !work.inputTraversal().linearCorridorSurfaces().getFirst().equals(actor.supportingSurface()))
            throw new IllegalArgumentException("service admission has foreign source, target or departure geometry");
        var knowledge = ResourceStorageNavigation.forEndpoint(state, source.endpoint());
        knowledge.requireRoute(work.inputTraversal().linearCorridorSurfaces());
        knowledge.requireRoute(work.workTraversal().linearCorridorSurfaces());
    }

    private static TraversalTopology topology(String leg, SubjectId workId, List<SurfaceAnchor> corridor) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:service-work-" + leg + "-"
                        + workId.value().replace(':', '-')), revision(corridor), workId, TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN), corridor);
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

    public record Plan(SurfaceAnchor inputStation, SurfaceAnchor workStation, TraversalTopology inputTraversal, TraversalTopology workTraversal) {
        public Plan {
            inputStation = Objects.requireNonNull(inputStation, "service input station"); workStation = Objects.requireNonNull(workStation, "service work station");
            inputTraversal = Objects.requireNonNull(inputTraversal, "service input traversal"); workTraversal = Objects.requireNonNull(workTraversal, "service work traversal");
            if (!inputTraversal.linearCorridorSurfaces().getLast().equals(inputStation)
                    || !workTraversal.linearCorridorSurfaces().getFirst().equals(inputStation)
                    || !workTraversal.linearCorridorSurfaces().getLast().equals(workStation)) {
                throw new IllegalArgumentException("service work corridors must retain both semantic stations");
            }
        }
    }
}
