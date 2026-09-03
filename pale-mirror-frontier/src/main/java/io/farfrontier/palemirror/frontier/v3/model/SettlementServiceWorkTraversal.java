package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiles one resident-owned service corridor without consulting loaded Minecraft blocks. */
public final class SettlementServiceWorkTraversal {
    private SettlementServiceWorkTraversal() { }

    public static Plan compileDecontamination(FrontierBootstrap bootstrap, Settlement settlement, ActorLocation worker,
                                              InfectionCell target, SubjectId workId) {
        Objects.requireNonNull(bootstrap, "service traversal bootstrap");
        Objects.requireNonNull(worker, "service traversal worker");
        Objects.requireNonNull(target, "service traversal infection target");
        Objects.requireNonNull(workId, "service traversal work id");
        Objects.requireNonNull(settlement, "service traversal settlement");
        SurfaceAnchor start = worker.supportingSurface();
        Set<BlockPosition> occupied = new java.util.LinkedHashSet<>(InfectionTreatmentWorksite.immutableOccupancy(bootstrap));
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("service settlement has no depot"));
        SettlementDepotServicePort depotPort = SettlementDepotServicePort.forDepot(depot);
        // A declared depot port is the sole exception to its building's otherwise solid
        // immutable occupancy.  Do not infer this from a chest location: the immutable port
        // explicitly owns these walkable hand-off surfaces.
        depotPort.ownedAccessSurfaces().forEach(surface -> {
            occupied.remove(surface.support()); occupied.remove(surface.support().offset(0, 1, 0)); occupied.remove(surface.support().offset(0, 2, 0));
        });
        for (SurfaceAnchor inputStation : depotPort.stations()) for (SurfaceAnchor workStation : InfectionTreatmentWorksite.candidates(bootstrap, target)) {
            try {
                List<SurfaceAnchor> input = BoundedPedestrianApproach.compile(bootstrap, start, inputStation, occupied,
                        (x, z) -> SurfaceAnchor.at(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 1), z), "service-work");
                List<SurfaceAnchor> work = BoundedPedestrianApproach.compile(bootstrap, inputStation, workStation, occupied,
                        (x, z) -> SurfaceAnchor.at(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 1), z), "service-work");
                return new Plan(inputStation, workStation,
                        topology("input", workId, input), topology("work", workId, work));
            } catch (IllegalArgumentException unavailable) {
                // The finite candidate order is the immutable admission policy.  A later HOT
                // collision records a conflict; it is never an excuse to re-enter this compiler.
            }
        }
        throw new IllegalArgumentException("service work has no bounded immutable route to infection treatment station: " + target);
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
