package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        return compile(bootstrap, workshop, worker, jobId, Set.of());
    }

    /**
     * Compiles a new COLD production approach against the current canonical bodies as well as
     * the immutable settlement shell.  A HOT actuator has no authority to push, overlap, or
     * route around another living actor; therefore its retained corridor must not be admitted
     * through a support already held by somebody other than this exact worker.
     */
    public static TraversalTopology compile(FrontierWorldState state, SettlementStructure workshop,
                                            SubjectId workerId, ActorLocation worker, SubjectId jobId) {
        Objects.requireNonNull(state, "production state");
        Objects.requireNonNull(workerId, "production worker id");
        Set<BlockPosition> occupiedBodies = new HashSet<>();
        state.actorLocations().forEach((actorId, location) -> {
            if (!actorId.equals(workerId) && location.condition().status() == ActorLifeStatus.ALIVE) {
                occupiedBodies.add(location.supportingSurface().support());
            }
        });
        return compile(state.bootstrap(), workshop, worker, jobId, occupiedBodies);
    }

    private static TraversalTopology compile(FrontierBootstrap bootstrap, SettlementStructure workshop,
                                             ActorLocation worker, SubjectId jobId, Set<BlockPosition> occupiedBodies) {
        Objects.requireNonNull(bootstrap, "production bootstrap"); Objects.requireNonNull(workshop, "production workshop");
        Objects.requireNonNull(worker, "production worker"); Objects.requireNonNull(jobId, "production job");
        SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(workshop);
        SurfaceAnchor start = worker.supportingSurface(); SurfaceAnchor exterior = port.exteriorApproach();
        Set<BlockPosition> blocked = immutableBodyObstacles(bootstrap, port, start, occupiedBodies);
        if (blocked.contains(start.support())) {
            throw new IllegalArgumentException("production worker shares its retained support with another living actor");
        }
        Map<TerrainColumn, SurfaceAnchor> localSurfaces = SettlementPedestrianGround.localSupports(bootstrap, workshop.settlementId());
        List<SurfaceAnchor> corridor = new ArrayList<>(BoundedPedestrianApproach.compile(bootstrap, start, exterior, blocked,
                // A local public circulation surface is a raised, plan-owned supporting block.
                // The free approach must therefore retain that exact datum instead of treating
                // its occupied block as the worker's body cell.  Everywhere else the immutable
                // terrain survey names the physical support directly; no loaded-world query or
                // alternate route is admitted here.
                (x, z) -> SettlementPedestrianGround.surveyedSupport(bootstrap, localSurfaces, x, z), "production-work"));
        List<SurfaceAnchor> ingress = port.topologyPort().ingressSurfaces();
        if (ingress.stream().anyMatch(surface -> blocked.contains(surface.support()))) {
            throw new IllegalArgumentException("production workshop ingress is occupied by another living actor");
        }
        corridor.addAll(ingress.subList(1, ingress.size()));
        corridor.add(port.inputStation()); corridor.add(port.workStation());
        // A just-finished exact worker is retained at its work station.  Its next admitted job
        // may therefore traverse a bounded return through the same named workshop surfaces;
        // TraversalTopology preserves each visit as a distinct node identity rather than
        // relocating the worker or selecting another workshop entrance.
        return TraversalTopology.corridor(new TraversalTopologyId("topology:production-work-" + jobId.value().replace(':', '-')),
                revision(corridor), workshop.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor);
    }

    private static Set<BlockPosition> immutableBodyObstacles(FrontierBootstrap bootstrap, SettlementWorkshopServicePort port,
                                                               SurfaceAnchor start, Set<BlockPosition> occupiedBodies) {
        Set<BlockPosition> blocked = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements()) {
            blocked.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
        }
        blocked.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        blocked.remove(start.support());
        List<SurfaceAnchor> permitted = new ArrayList<>(port.topologyPort().ingressSurfaces());
        permitted.add(port.inputStation()); permitted.add(port.workStation());
        permitted.forEach(surface -> blocked.remove(surface.support()));
        blocked.addAll(Objects.requireNonNull(occupiedBodies, "production body occupancy"));
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
