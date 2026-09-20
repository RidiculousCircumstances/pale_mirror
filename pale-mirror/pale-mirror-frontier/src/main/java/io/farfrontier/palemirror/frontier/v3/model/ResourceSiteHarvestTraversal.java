package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.HashSet;
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
    private static final int WORK_RETURN_DISTANCE = 4;
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
        // The terminal field-edge support is not another harvest cursor.  It records the
        // collision-clear station retained by the terminal receipt, so a later ordinary
        // ingress never has to recreate the exact farmer inside the replanted final crop.
        corridor.addAll(workReturnCorridor(bootstrap, site));
        return TraversalTopology.corridor(new TraversalTopologyId("topology:field-work-" + jobId.value().replace(':', '-')),
                revision(corridor), site.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor);
    }

    /**
     * Compiles the immutable, collision-clear field-edge departure station after the final
     * harvested crop.  It is deliberately not another process route or cursor: the harvest
     * cursor has already closed.  It is the local physical destination for the same released
     * body while the canonical site passes through output, growth and successor admission.
     *
     * <p>The former FARM-centre target was a raw structure coordinate. A no-AI local actuator
     * attempted to cross its wall, remained on crop 63 indefinitely, then could disappear at a
     * later release. This compiler chooses only a four-cell cardinal ray whose every support is
     * surveyed, in bounds, outside field ownership and outside every immutable structure/organ
     * body. It never probes Minecraft, reuses a runtime path, or grants the actor a second
     * canonical movement authority.</p>
     */
    public static SurfaceAnchor workReturnSurface(FrontierBootstrap bootstrap, ResourceSite site) {
        return workReturnCorridor(bootstrap, site).getLast();
    }

    /** The fixed edge-station tail is retained outside the immutable 64 crop cursors. */
    public static int workReturnStationCount() { return WORK_RETURN_DISTANCE; }

    private static List<SurfaceAnchor> workReturnCorridor(FrontierBootstrap bootstrap, ResourceSite site) {
        Objects.requireNonNull(bootstrap, "harvest return bootstrap"); Objects.requireNonNull(site, "harvest return site");
        if (site.kind() != ResourceSiteKind.WHEAT_FIELD) throw new IllegalArgumentException("resource-site return only supports wheat fields");
        SurfaceAnchor terminal = new SurfaceAnchor(site.cropSlots().getLast().offset(0, -1, 0));
        Set<BlockPosition> blocked = immutableBodyObstacles(bootstrap, site, null);
        for (int[] direction : List.of(new int[] {1, 0}, new int[] {0, -1}, new int[] {-1, 0}, new int[] {0, 1})) {
            List<SurfaceAnchor> corridor = new ArrayList<>(WORK_RETURN_DISTANCE);
            boolean clear = true;
            for (int step = 1; step <= WORK_RETURN_DISTANCE; step++) {
                int x = terminal.x() + direction[0] * step, z = terminal.z() + direction[1] * step;
                SurfaceAnchor surface = SurfaceAnchor.at(x, bootstrap.terrain().supportYAt(x, z), z);
                if (!bootstrap.bounds().contains(surface.support()) || surface.y() != terminal.y()
                        || site.managedSlots().contains(surface.support()) || blocked.contains(surface.support())) {
                    clear = false; break;
                }
                corridor.add(surface);
            }
            if (clear) return List.copyOf(corridor);
        }
        throw new IllegalArgumentException("resource-site final crop has no declared clear work return station: " + site.id().value());
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
            if (firstWorkstation == null || !support.equals(firstWorkstation)) blocked.add(support);
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
