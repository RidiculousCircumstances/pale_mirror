package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiles one resident-owned service corridor without consulting loaded Minecraft blocks. */
public final class SettlementServiceWorkTraversal {
    private SettlementServiceWorkTraversal() { }

    public static Plan compileDecontamination(FrontierBootstrap bootstrap, ActorLocation worker,
                                              InfectionCell target, SubjectId workId) {
        Objects.requireNonNull(bootstrap, "service traversal bootstrap");
        Objects.requireNonNull(worker, "service traversal worker");
        Objects.requireNonNull(target, "service traversal infection target");
        Objects.requireNonNull(workId, "service traversal work id");
        SurfaceAnchor start = worker.supportingSurface();
        Set<BlockPosition> occupied = InfectionTreatmentWorksite.immutableOccupancy(bootstrap);
        for (SurfaceAnchor station : InfectionTreatmentWorksite.candidates(bootstrap, target)) {
            try {
                List<SurfaceAnchor> corridor = BoundedPedestrianApproach.compile(bootstrap, start, station, occupied,
                        (x, z) -> SurfaceAnchor.at(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 1), z), "service-work");
                return new Plan(station, TraversalTopology.corridor(new TraversalTopologyId("topology:service-work-"
                        + workId.value().replace(':', '-')), revision(corridor), workId, TraversalKind.PEDESTRIAN,
                        Set.of(TraversalCapability.PEDESTRIAN), corridor));
            } catch (IllegalArgumentException unavailable) {
                // The finite candidate order is the immutable admission policy.  A later HOT
                // collision records a conflict; it is never an excuse to re-enter this compiler.
            }
        }
        throw new IllegalArgumentException("service work has no bounded immutable route to infection treatment station: " + target);
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

    public record Plan(SurfaceAnchor station, TraversalTopology traversal) {
        public Plan {
            station = Objects.requireNonNull(station, "service work station");
            traversal = Objects.requireNonNull(traversal, "service work traversal");
            if (!traversal.linearCorridorSurfaces().getLast().equals(station)) {
                throw new IllegalArgumentException("service work corridor must end at its retained station");
            }
        }
    }
}
