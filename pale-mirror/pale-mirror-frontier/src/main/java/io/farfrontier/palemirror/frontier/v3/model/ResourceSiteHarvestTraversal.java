package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Transitional compiler for the legacy serialized field-route cache and segment bounds.
 * Normal HOT movement now pursues a semantic goal through Minecraft navigation, and normal
 * COLD movement derives an ephemeral known path from the actor's actual body. The resulting
 * topology must not regain authority as a sequence of physical waypoints; retiring this
 * compiler from job admission and persistence remains part of the connected migration.
 */
public final class ResourceSiteHarvestTraversal {
    private static final int WORK_RETURN_DISTANCE = 4;
    private ResourceSiteHarvestTraversal() { }

    /** Current executor capability: one adjacent, single-height, 64-cell graybox corridor. */
    public static boolean supportsCurrentHarvest(ResourceSite site) {
        Objects.requireNonNull(site, "harvest site");
        List<ResourceFieldLayout.Cell> cells = site.layout().cells();
        if (site.layout().revision() != 1 || cells.size() != ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS) return false;
        for (int index = 0; index < cells.size(); index++) {
            ResourceFieldLayout.Cell cell = cells.get(index);
            if (!cell.workstation().equals(cell.soil())) return false;
            if (index == 0) continue;
            SurfaceAnchor previous = cells.get(index - 1).workstation();
            SurfaceAnchor current = cell.workstation();
            if (previous.y() != current.y()
                    || Math.abs((long) previous.x() - current.x())
                    + Math.abs((long) previous.z() - current.z()) != 1L) return false;
        }
        return true;
    }

    /** A fresh, explicitly authored layout can be worked in bounded segments. */
    public static boolean supportsSegmentedHarvest(ResourceSite site) {
        Objects.requireNonNull(site, "harvest site");
        return site.layout().revision() == 1 && !site.layout().cells().isEmpty()
                && site.layout().cells().size() <= ResourceFieldLayout.MAX_CELLS;
    }

    public static TraversalTopology compile(FrontierBootstrap bootstrap, ResourceSite site, ActorLocation worker, SubjectId jobId) {
        return compilePlan(bootstrap, site, worker, jobId).topology();
    }

    /** One retained route with an explicit crop boundary; return length is not a field-size formula. */
    public static Plan compilePlan(FrontierBootstrap bootstrap, ResourceSite site, ActorLocation worker, SubjectId jobId) {
        Objects.requireNonNull(bootstrap, "harvest bootstrap"); Objects.requireNonNull(site, "harvest site");
        Objects.requireNonNull(worker, "harvest worker"); Objects.requireNonNull(jobId, "harvest job");
        if (!supportsCurrentHarvest(site))
            throw new IllegalArgumentException("resource-site harvest executor cannot admit this field layout yet");
        SurfaceAnchor start = worker.supportingSurface();
        List<SurfaceAnchor> workstations = site.layout().cells().stream().map(ResourceFieldLayout.Cell::workstation).toList();
        SurfaceAnchor first = workstations.getFirst();
        Set<BlockPosition> blocked = immutableBodyObstacles(bootstrap, site, first.support());
        List<SurfaceAnchor> approach = BoundedPedestrianApproach.compile(bootstrap, start, first, blocked,
                surveyedFieldSurface(bootstrap, site), "field-work");
        List<SurfaceAnchor> corridor = new ArrayList<>(approach);
        int firstCropCursor = corridor.size() - 1;
        for (int index = 1; index < workstations.size(); index++) corridor.add(workstations.get(index));
        // The field-edge support is not another harvest cursor. It leaves the replanted
        // final crop before the same retained journey continues to the depot port.
        corridor.addAll(workReturnCorridor(bootstrap, site));
        appendDepotServiceRoute(bootstrap, site, corridor);
        return new Plan(TraversalTopology.corridor(new TraversalTopologyId("topology:field-work-" + jobId.value().replace(':', '-')),
                revision(corridor), site.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor), firstCropCursor,
                0, site.layout().cells().size(), java.util.stream.IntStream.range(0, site.layout().cells().size())
                        .mapToObj(index -> firstCropCursor + index).toList());
    }

    /**
     * Compiles only the next bounded portion of a larger field. The current hand's free
     * capacity bounds how many further yielding cells can be visited before a depot return.
     * A non-yielding segment can be replaced at its last station without moving or resetting
     * the field's stable CellId progress. The optional depot tail is retained now so a full
     * hand never has to invent a route after its physical crop effect.
     */
    public static Plan compileSegmentPlan(FrontierBootstrap bootstrap, ResourceSite site, ActorLocation worker,
                                          SubjectId jobId, int firstSlot, int carriedQuantity) {
        return compileSegmentPlan(bootstrap, site, worker, jobId, firstSlot, carriedQuantity, Set.of());
    }

    /** Rebuilds only the next bounded segment after an observed field obstruction. */
    public static Plan compileSegmentPlan(FrontierBootstrap bootstrap, ResourceSite site, ActorLocation worker,
                                          SubjectId jobId, int firstSlot, int carriedQuantity,
                                          Set<BlockPosition> unavailableSupports) {
        Objects.requireNonNull(bootstrap, "harvest segment bootstrap"); Objects.requireNonNull(site, "harvest segment site");
        Objects.requireNonNull(worker, "harvest segment worker"); Objects.requireNonNull(jobId, "harvest segment job");
        Objects.requireNonNull(unavailableSupports, "unavailable field supports");
        List<ResourceFieldLayout.Cell> cells = site.layout().cells();
        if (firstSlot < 0 || firstSlot >= cells.size() || carriedQuantity < 0 || carriedQuantity >= 64)
            throw new IllegalArgumentException("harvest segment requires its next cell and bounded carried hand");
        SurfaceAnchor start = worker.supportingSurface();
        var surveyed = surveyedFieldSurface(bootstrap, site);
        Set<BlockPosition> blocked = segmentObstacles(bootstrap, site);
        blocked.addAll(unavailableSupports);
        if (unavailableSupports.contains(start.support()))
            throw new IllegalArgumentException("field worker's retained station is obstructed");
        // A returned farmer may begin its next batch at the depot's declared
        // service port, which belongs to immutable structure occupancy but is
        // nevertheless an authorized physical pedestrian station.
        blocked.remove(start.support());
        int maxEnd = Math.min(cells.size(), firstSlot + 64 - carriedQuantity);
        // A known foreign work target is not a waypoint. Retain only the next
        // reachable target prefix; the owning work process will account the
        // obstructed CellIds at its exact stationary boundary.
        for (int index = firstSlot; index < maxEnd; index++) {
            if (unavailableSupports.contains(cells.get(index).soil().support())) {
                maxEnd = index;
                break;
            }
        }
        if (maxEnd == firstSlot)
            throw new IllegalArgumentException("next field work goal is obstructed");
        // The ordinary case succeeds on the first candidate. Backing off is only a bounded
        // topology-cap adjustment; it never skips a cell or changes their declared order.
        for (int end = maxEnd; end > firstSlot; end--) {
            try {
                List<SurfaceAnchor> corridor = new ArrayList<>(); corridor.add(start);
                int firstCrop = -1;
                List<Integer> cropCursors = new ArrayList<>();
                for (int index = firstSlot; index < end; index++) {
                    SurfaceAnchor target = cells.get(index).workstation();
                    List<SurfaceAnchor> edge = BoundedPedestrianApproach.compile(bootstrap, corridor.getLast(), target,
                            blocked, surveyed, "field-work-segment");
                    corridor.addAll(edge.subList(1, edge.size()));
                    if (firstCrop < 0) firstCrop = corridor.size() - 1;
                    cropCursors.add(corridor.size() - 1);
                    if (corridor.size() >= TraversalTopology.MAX_NODES) break;
                }
                if (firstCrop < 0 || cropCursors.size() != end - firstSlot
                        || corridor.size() >= TraversalTopology.MAX_NODES) continue;
                appendSegmentDepotServiceRoute(bootstrap, site, corridor, blocked, surveyed);
                if (corridor.size() > TraversalTopology.MAX_NODES) continue;
                TraversalTopology route = TraversalTopology.corridor(
                        new TraversalTopologyId("topology:field-work-" + jobId.value().replace(':', '-') + "-segment-" + firstSlot),
                        revision(corridor), site.id(), TraversalKind.PEDESTRIAN,
                        Set.of(TraversalCapability.PEDESTRIAN), corridor);
                return new Plan(route, firstCrop, firstSlot, end, cropCursors);
            } catch (IllegalArgumentException unavailable) {
                if (end == firstSlot + 1) throw unavailable;
            }
        }
        throw new IllegalArgumentException("field worker has no bounded segment and depot route: " + site.id().value());
    }

    /** The last blocked cell needs a depot return from the *current* worker station. */
    public static Plan compileReturnOnlyPlan(FrontierBootstrap bootstrap, ResourceSite site, ActorLocation worker,
                                             SubjectId jobId, Set<BlockPosition> unavailableSupports) {
        Objects.requireNonNull(bootstrap, "harvest return bootstrap"); Objects.requireNonNull(site, "harvest return site");
        Objects.requireNonNull(worker, "harvest return worker"); Objects.requireNonNull(jobId, "harvest return job");
        Objects.requireNonNull(unavailableSupports, "unavailable field supports");
        if (!supportsSegmentedHarvest(site)) throw new IllegalArgumentException("unsupported return field layout");
        Set<BlockPosition> blocked = segmentObstacles(bootstrap, site);
        blocked.addAll(unavailableSupports);
        SurfaceAnchor start = worker.supportingSurface();
        if (unavailableSupports.contains(start.support()))
            throw new IllegalArgumentException("field worker's retained return station is obstructed");
        blocked.remove(start.support());
        List<SurfaceAnchor> corridor = new ArrayList<>();
        corridor.add(start);
        appendSegmentDepotServiceRoute(bootstrap, site, corridor, blocked, surveyedFieldSurface(bootstrap, site));
        if (corridor.size() < 2 || corridor.size() > TraversalTopology.MAX_NODES)
            throw new IllegalArgumentException("field return has no bounded depot route");
        TraversalTopology route = TraversalTopology.corridor(
                new TraversalTopologyId("topology:field-work-" + jobId.value().replace(':', '-') + "-return"),
                revision(corridor), site.id(), TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN), corridor);
        int completed = site.layout().cells().size();
        return new Plan(route, -1, completed, completed, List.of());
    }

    public record Plan(TraversalTopology topology, int firstCropCursor,
                       int segmentStartCropSlot, int segmentEndCropSlot, List<Integer> cropRouteCursors) {
        public Plan {
            Objects.requireNonNull(topology, "field-work topology");
            cropRouteCursors = List.copyOf(Objects.requireNonNull(cropRouteCursors, "field-work crop route cursors"));
            boolean returnOnly = firstCropCursor == -1 && cropRouteCursors.isEmpty()
                    && segmentStartCropSlot == segmentEndCropSlot;
            if (returnOnly && (segmentStartCropSlot < 1 || topology.linearCorridorSurfaces().size() < 2))
                throw new IllegalArgumentException("field return-only route lacks completed work or a depot edge");
            if (!returnOnly && (firstCropCursor < 0 || firstCropCursor >= topology.linearCorridorSurfaces().size()
                    || segmentStartCropSlot < 0 || segmentEndCropSlot <= segmentStartCropSlot
                    || segmentEndCropSlot - segmentStartCropSlot > 64
                    || cropRouteCursors.size() != segmentEndCropSlot - segmentStartCropSlot
                    || cropRouteCursors.getFirst() != firstCropCursor
                    || cropRouteCursors.getLast() >= topology.linearCorridorSurfaces().size() - 1
                    || !strictlyIncreasing(cropRouteCursors)))
                throw new IllegalArgumentException("field-work first crop cursor is invalid");
        }
    }

    private static boolean strictlyIncreasing(List<Integer> cursors) {
        for (int index = 1; index < cursors.size(); index++)
            if (cursors.get(index) <= cursors.get(index - 1)) return false;
        return true;
    }

    private static void appendDepotServiceRoute(FrontierBootstrap bootstrap, ResourceSite site,
                                                List<SurfaceAnchor> corridor) {
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(site.settlementId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("field has no settlement"));
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("field settlement has no depot"));
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        Set<BlockPosition> blocked = immutableBodyObstacles(bootstrap, site, null);
        port.ownedAccessSurfaces().forEach(surface -> {
            blocked.remove(surface.support()); blocked.remove(surface.support().offset(0, 1, 0));
            blocked.remove(surface.support().offset(0, 2, 0));
        });
        SurfaceAnchor from = corridor.getLast();
        for (SurfaceAnchor station : port.stations()) {
            try {
                List<SurfaceAnchor> route = BoundedPedestrianApproach.compile(bootstrap, from, station, blocked,
                        surveyedFieldSurface(bootstrap, site), "field-depot-delivery");
                corridor.addAll(route.subList(1, route.size()));
                return;
            } catch (IllegalArgumentException unavailable) {
                // The authored finite service stations are alternatives at admission only.
            }
        }
        throw new IllegalArgumentException("field worker has no bounded route to its depot service port: " + site.id().value());
    }

    private static void appendSegmentDepotServiceRoute(FrontierBootstrap bootstrap, ResourceSite site,
                                                       List<SurfaceAnchor> corridor, Set<BlockPosition> immutableBlocked,
                                                       BoundedPedestrianApproach.SurveyedSurface surveyed) {
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(site.settlementId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("field has no settlement"));
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("field settlement has no depot"));
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        Set<BlockPosition> blocked = new HashSet<>(immutableBlocked);
        port.ownedAccessSurfaces().forEach(surface -> {
            blocked.remove(surface.support()); blocked.remove(surface.support().offset(0, 1, 0));
            blocked.remove(surface.support().offset(0, 2, 0));
        });
        SurfaceAnchor from = corridor.getLast();
        for (SurfaceAnchor station : port.stations()) {
            try {
                List<SurfaceAnchor> route = BoundedPedestrianApproach.compile(bootstrap, from, station, blocked,
                        surveyed, "field-segment-depot-delivery");
                corridor.addAll(route.subList(1, route.size()));
                return;
            } catch (IllegalArgumentException unavailable) {
                // Only these authored service stations are alternatives at compilation.
            }
        }
        throw new IllegalArgumentException("field segment has no bounded route to its depot: " + site.id().value());
    }

    static Set<BlockPosition> segmentObstacles(FrontierBootstrap bootstrap, ResourceSite site) {
        return ResourceSiteHarvestKnownGeometry.occupiedBodies(bootstrap, site);
    }

    /**
     * Compiles the immutable, collision-clear field-edge departure station after the final
     * harvested crop. The crop-work cursor has closed, but these four stations are still
     * ordinary retained pedestrian edges of the same job. This is a field-edge handoff
     * station, not the settlement depot's physical delivery port.
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

    /** The fixed local field-exit prefix precedes the variable depot-service leg. */
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
     * terrain support everywhere except the settlement's declared public ground and immutable
     * farmland support in crop columns.  Every non-first crop surface remains an obstacle during
     * the approach and only the declared first workstation is entered by the bounded compiler.
     */
    static BoundedPedestrianApproach.SurveyedSurface surveyedFieldSurface(FrontierBootstrap bootstrap, ResourceSite site) {
        return ResourceSiteHarvestKnownGeometry.surveyedSupports(bootstrap, site);
    }

    private static Set<BlockPosition> immutableBodyObstacles(FrontierBootstrap bootstrap, ResourceSite site, BlockPosition firstWorkstation) {
        Set<BlockPosition> blocked = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements()) {
            blocked.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
        }
        blocked.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        // Irrigation is a managed water source at the field perimeter, not pedestrian ground.
        // Retaining it as an ordinary approach cell produces a route which the shared physical
        // arrival provider must (correctly) reject as unsupported.  Make that impossible in the
        // immutable compiler rather than weakening the provider or teaching HOT a coordinate
        // exception.
        blocked.addAll(site.irrigationSlots());
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
