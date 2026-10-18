package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import java.util.*;

/** Rebuildable local geometry only. Regional terrain/profile revisions and the
 * exact declaration establish validity, never stock, reservations or occupancy. */
final class ServicePointGeometryCache {
    record Request(SubjectId facility, SubjectId settlement, SurfaceAnchor station,
                   ServiceAccessBoundary boundary, SurfaceAnchor center,
                   List<SurfaceAnchor> preferred, int population, boolean waiting) {
        Request { preferred = List.copyOf(preferred); }
    }
    private record Entry(Request request, KnownPedestrianRouteKnowledge knowledge,
                         Set<PedestrianGeometryRevision.Region> dependencies, ServiceAccessPoint point) { }
    private final Map<SubjectId, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    synchronized ServiceAccessPoint get(SubjectId id, Request request, KnownPedestrianRouteKnowledge knowledge) {
        var previous = entries.get(id);
        if (previous != null && previous.request().equals(request) && compatible(previous, knowledge)) {
            if (previous.knowledge() != knowledge) entries.put(id, new Entry(request, knowledge, previous.dependencies(), previous.point()));
            return previous.point();
        }
        var egress = ServiceClearanceTargets.egressRegion(request.boundary(), knowledge);
        var waiting = request.waiting() ? ServiceWaitingTargets.select(request.boundary(), knowledge, request.center(),
                request.preferred(), egress, request.population()) : List.<SurfaceAnchor>of();
        var point = new ServiceAccessPoint(request.facility(), request.settlement(), request.station(), request.boundary(), waiting, egress);
        entries.put(id, new Entry(request, knowledge, dependencies(request), point));
        if (entries.size() > 64) entries.remove(entries.keySet().iterator().next());
        return point;
    }
    private static boolean compatible(Entry previous, KnownPedestrianRouteKnowledge knowledge) {
        if (previous.knowledge() == knowledge) return true;
        var before = previous.knowledge().geometry().regionalRevision();
        var after = knowledge.geometry().regionalRevision();
        return before.isPresent() && after.isPresent() && after.orElseThrow().matches(before.orElseThrow(), previous.dependencies());
    }
    private static Set<PedestrianGeometryRevision.Region> dependencies(Request request) {
        var result = new HashSet<PedestrianGeometryRevision.Region>();
        if (request.waiting()) {
            include(result, request.center(), ServiceWaitingTargets.radius(request.population()) + 1);
            request.preferred().forEach(p -> include(result, p, 1));
        }
        request.boundary().occupiedSurfaces().forEach(p -> include(result, p, 2));
        return Set.copyOf(result);
    }
    private static void include(Set<PedestrianGeometryRevision.Region> result, SurfaceAnchor center, int radius) {
        for (int x = (center.x() - radius) >> 4; x <= (center.x() + radius) >> 4; x++)
            for (int z = (center.z() - radius) >> 4; z <= (center.z() + radius) >> 4; z++)
                result.add(new PedestrianGeometryRevision.Region(x, z));
    }
}
