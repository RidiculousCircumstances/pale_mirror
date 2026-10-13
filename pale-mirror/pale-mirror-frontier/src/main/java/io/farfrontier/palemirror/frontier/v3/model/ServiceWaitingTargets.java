package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Shared temporary-buffer geometry. Point providers supply orientation, never meal/job policy. */
final class ServiceWaitingTargets {
    private ServiceWaitingTargets() { }
    static List<SurfaceAnchor> select(ServiceAccessBoundary boundary, KnownPedestrianRouteKnowledge knowledge,
            SurfaceAnchor center, List<SurfaceAnchor> preferred, Set<SurfaceAnchor> egress, int population) {
        Set<SurfaceAnchor> waiting = new LinkedHashSet<>();
        for (SurfaceAnchor surface : preferred) add(waiting, boundary, knowledge, surface.x(), surface.z());
        int radius = Math.min(16, Math.max(2, (int) Math.ceil(Math.sqrt(population))));
        for (int ring = 1; ring <= radius; ring++)
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++)
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring)
                        add(waiting, boundary, knowledge, center.x() + dx, center.z() + dz);
        waiting.removeIf(surface -> egress.contains(surface) || !knowledge.traversable(List.of(surface)));
        return List.copyOf(waiting);
    }
    private static void add(Set<SurfaceAnchor> waiting, ServiceAccessBoundary boundary,
            KnownPedestrianRouteKnowledge knowledge, int x, int z) {
        for (SurfaceAnchor supported : knowledge.surfacesAt(x, z))
            if (boundary.cleared(supported.standingBody())) waiting.add(supported);
    }
}
