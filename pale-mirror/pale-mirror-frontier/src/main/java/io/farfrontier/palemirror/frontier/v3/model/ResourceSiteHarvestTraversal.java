package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles the one retained pedestrian corridor for a named field worker.
 *
 * <p>The corridor starts at the worker's canonical support surface, reaches the first field
 * workstation through immutable surveyed terrain and structure occupancy, then visits every
 * crop workstation in the field's stable serpentine order.  It is deliberately a pure compiler:
 * it does not inspect loaded blocks, ask Minecraft to find a path, or choose an alternate route
 * after an obstruction.  A HOT scene may only move toward the next retained surface.</p>
 */
public final class ResourceSiteHarvestTraversal {
    private ResourceSiteHarvestTraversal() { }

    public static TraversalTopology compile(FrontierBootstrap bootstrap, ResourceSite site, ActorLocation worker, SubjectId jobId) {
        Objects.requireNonNull(bootstrap, "harvest bootstrap"); Objects.requireNonNull(site, "harvest site");
        Objects.requireNonNull(worker, "harvest worker"); Objects.requireNonNull(jobId, "harvest job");
        SurfaceAnchor start = worker.supportingSurface();
        List<SurfaceAnchor> workstations = site.cropSlots().stream().map(slot -> new SurfaceAnchor(slot.offset(0, -1, 0))).toList();
        SurfaceAnchor first = workstations.getFirst();
        Set<BlockPosition> blocked = immutableBodyObstacles(bootstrap, site, first.support());
        List<SurfaceAnchor> approach = BoundedPedestrianApproach.compile(bootstrap, start, first, blocked,
                surveyedFieldSurface(bootstrap, site), "field-work");
        List<SurfaceAnchor> corridor = new ArrayList<>(approach);
        for (int index = 1; index < workstations.size(); index++) corridor.add(workstations.get(index));
        if (new LinkedHashSet<>(corridor).size() != corridor.size()) {
            throw new IllegalArgumentException("field-work corridor repeats a semantic surface");
        }
        return TraversalTopology.corridor(new TraversalTopologyId("topology:field-work-" + jobId.value().replace(':', '-')),
                revision(corridor), site.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor);
    }

    /**
     * A crop occupies the feet cell above its farmland, not a second walkable floor.  The
     * historic generic survey also treated ordinary terrain as a hypothetical extra surface one
     * cell above its real support.  That can retain a COLD body in air next to a field, which
     * can never be admitted honestly by HOT.  This field-owned survey instead names the actual
     * terrain support everywhere and the immutable farmland support in crop columns.  Every
     * non-first crop surface remains an obstacle during the approach and only the declared
     * first workstation is entered by the bounded compiler.
     */
    private static BoundedPedestrianApproach.SurveyedSurface surveyedFieldSurface(FrontierBootstrap bootstrap, ResourceSite site) {
        java.util.Map<Long, SurfaceAnchor> fieldSurfaces = new java.util.HashMap<>();
        for (BlockPosition crop : site.cropSlots()) {
            fieldSurfaces.put(column(crop.x(), crop.z()), new SurfaceAnchor(crop.offset(0, -1, 0)));
        }
        return (x, z) -> fieldSurfaces.getOrDefault(column(x, z),
                SurfaceAnchor.at(x, bootstrap.terrain().supportYAt(x, z), z));
    }

    private static long column(int x, int z) {
        return (Integer.toUnsignedLong(x) << 32) | Integer.toUnsignedLong(z);
    }

    private static Set<BlockPosition> immutableBodyObstacles(FrontierBootstrap bootstrap, ResourceSite site, BlockPosition firstWorkstation) {
        Set<BlockPosition> blocked = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements()) {
            blocked.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
        }
        blocked.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        // Crop supports are workstations, never a shortcut through a field.  The exact first
        // station is the declared endpoint and is therefore intentionally left available.
        for (BlockPosition crop : site.cropSlots()) {
            BlockPosition support = crop.offset(0, -1, 0);
            if (!support.equals(firstWorkstation)) blocked.add(support);
        }
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
