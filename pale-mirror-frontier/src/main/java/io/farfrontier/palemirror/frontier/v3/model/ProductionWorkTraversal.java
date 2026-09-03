package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles the one retained pedestrian corridor from an exact crafter to a workshop work station.
 *
 * <p>The free terrain approach ends at the workshop's declared exterior port; the final ingress,
 * input and work surfaces are appended from that same semantic port. Neither a COLD reducer nor
 * a HOT navigator may later choose a nearer wall, a different entrance or an alternate station.</p>
 */
public final class ProductionWorkTraversal {
    private ProductionWorkTraversal() { }

    public static TraversalTopology compile(FrontierBootstrap bootstrap, SettlementStructure workshop,
                                            ActorLocation worker, SubjectId jobId) {
        Objects.requireNonNull(bootstrap, "production bootstrap"); Objects.requireNonNull(workshop, "production workshop");
        Objects.requireNonNull(worker, "production worker"); Objects.requireNonNull(jobId, "production job");
        SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(workshop);
        SurfaceAnchor start = worker.supportingSurface(); SurfaceAnchor exterior = port.exteriorApproach();
        Set<BlockPosition> blocked = immutableBodyObstacles(bootstrap, port, start);
        List<SurfaceAnchor> corridor = new ArrayList<>(BoundedPedestrianApproach.compile(bootstrap, start, exterior, blocked,
                (x, z) -> SurfaceAnchor.at(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 1), z), "production-work"));
        List<SurfaceAnchor> ingress = port.topologyPort().ingressSurfaces();
        corridor.addAll(ingress.subList(1, ingress.size()));
        corridor.add(port.inputStation()); corridor.add(port.workStation());
        if (new LinkedHashSet<>(corridor).size() != corridor.size()) {
            throw new IllegalArgumentException("production-work corridor repeats a semantic surface");
        }
        return TraversalTopology.corridor(new TraversalTopologyId("topology:production-work-" + jobId.value().replace(':', '-')),
                revision(corridor), workshop.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor);
    }

    private static Set<BlockPosition> immutableBodyObstacles(FrontierBootstrap bootstrap, SettlementWorkshopServicePort port,
                                                               SurfaceAnchor start) {
        Set<BlockPosition> blocked = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements()) {
            blocked.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
        }
        blocked.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        blocked.remove(start.support());
        List<SurfaceAnchor> permitted = new ArrayList<>(port.topologyPort().ingressSurfaces());
        permitted.add(port.inputStation()); permitted.add(port.workStation());
        permitted.forEach(surface -> blocked.remove(surface.support()));
        return blocked;
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
