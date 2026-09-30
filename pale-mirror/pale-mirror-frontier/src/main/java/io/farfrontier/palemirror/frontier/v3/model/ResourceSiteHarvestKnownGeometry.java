package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Field-owned, immutable-knowledge input for pedestrian navigation and legacy route replay. */
public final class ResourceSiteHarvestKnownGeometry {
    private ResourceSiteHarvestKnownGeometry() { }

    public static Set<BlockPosition> occupiedBodies(FrontierBootstrap bootstrap, ResourceSite site) {
        Objects.requireNonNull(bootstrap, "field navigation bootstrap");
        Objects.requireNonNull(site, "field navigation site");
        Set<BlockPosition> blocked = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements())
            blocked.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
        blocked.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        blocked.addAll(site.irrigationSlots());
        // Field supports remain pedestrian surfaces even after their crop outcome changes.
        return blocked;
    }

    /** The real terrain support, except for declared settlement ground and field farmland. */
    public static BoundedPedestrianApproach.SurveyedSurface surveyedSupports(FrontierBootstrap bootstrap, ResourceSite site) {
        Objects.requireNonNull(bootstrap, "field navigation bootstrap");
        Objects.requireNonNull(site, "field navigation site");
        Map<Long, SurfaceAnchor> fieldSurfaces = new HashMap<>();
        for (BlockPosition crop : site.cropSlots())
            fieldSurfaces.put(column(crop.x(), crop.z()), new SurfaceAnchor(crop.offset(0, -1, 0)));
        Map<TerrainColumn, SurfaceAnchor> localGround = SettlementPedestrianGround.localSupports(bootstrap, site.settlementId());
        return (x, z) -> fieldSurfaces.getOrDefault(column(x, z),
                SettlementPedestrianGround.surveyedSupport(bootstrap, localGround, x, z));
    }

    /** Field-specific knowledge overlay; the work owner supplies only its typed goal. */
    public static List<SurfaceAnchor> route(FrontierWorldState state, ResourceSite site,
                                             ResourceFieldCycle cycle, ResourceSiteHarvestGoal goal,
                                             SurfaceAnchor start, SettlementDepotServicePort depotPort) {
        Objects.requireNonNull(state, "field route state");
        Objects.requireNonNull(site, "field route site");
        Objects.requireNonNull(cycle, "field route cycle");
        Objects.requireNonNull(goal, "field route goal");
        Objects.requireNonNull(start, "field route start");
        Objects.requireNonNull(depotPort, "field route depot passage");
        if (!site.settlementId().equals(depotPort.settlementId()))
            throw new IllegalArgumentException("field route has a foreign depot passage");
        var surveyed = surveyedSupports(state.bootstrap(), site);
        SurfaceAnchor knownAtStart = surveyed.at(start.x(), start.z());
        if (!state.bootstrap().bounds().contains(start.support())
                // A witnessed HOT body may be one support above or below the immutable survey.
                || Math.abs(start.y() - knownAtStart.y()) > 1
                    && !depotPort.ownedAccessSurfaces().contains(start))
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    "field worker body is not on retained known support");
        Set<BlockPosition> occupied = occupiedBodies(state.bootstrap(), site);
        for (ResourceFieldLayout.Cell cell : cycle.layout().cells()) {
            ResourceFieldCycle.CellState condition = cycle.cell(cell.id());
            if (condition.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                    || condition.soil() == ResourceFieldCycle.Soil.OBSTRUCTED)
                occupied.add(cell.soil().support());
            if (condition.workAccessBlocked())
                occupied.add(cell.workstation().support().offset(0, 2, 0));
        }
        // An authored depot passage is walkable infrastructure, not an arbitrary
        // caller-owned set of cells to erase from the route's world geometry.
        for (SurfaceAnchor surface : depotPort.ownedAccessSurfaces()) clear(occupied, surface);
        if (!start.equals(knownAtStart) && !depotPort.ownedAccessSurfaces().contains(start)) {
            // Preserve only the one physically observed off-survey start, never
            // neighbouring cells or a guessed alternative field entrance.
            clear(occupied, start);
        } else if (blocked(start, occupied)) {
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    "field worker's known support is no longer traversable");
        }
        return KnownPedestrianNavigation.route(state.bootstrap(), start, goal.movementOrder(), occupied, surveyed);
    }

    private static boolean blocked(SurfaceAnchor surface, Set<BlockPosition> occupied) {
        return occupied.contains(surface.support()) || occupied.contains(surface.support().offset(0, 1, 0))
                || occupied.contains(surface.support().offset(0, 2, 0));
    }

    private static void clear(Set<BlockPosition> occupied, SurfaceAnchor surface) {
        occupied.remove(surface.support());
        occupied.remove(surface.support().offset(0, 1, 0));
        occupied.remove(surface.support().offset(0, 2, 0));
    }

    private static long column(int x, int z) {
        return (Integer.toUnsignedLong(x) << 32) | Integer.toUnsignedLong(z);
    }
}
