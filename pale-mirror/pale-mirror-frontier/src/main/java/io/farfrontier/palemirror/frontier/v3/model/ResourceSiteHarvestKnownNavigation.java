package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;

/**
 * Derives a COLD pedestrian path from the actor's current body to its semantic
 * field/depot goal. The result is ephemeral geometry, never a work cursor or
 * proof that an unloaded Minecraft block was observed.
 */
public final class ResourceSiteHarvestKnownNavigation {
    private ResourceSiteHarvestKnownNavigation() { }

    /** A bounded epistemic gap, distinct from a stale owner or malformed canonical goal. */
    public static final class KnowledgeUnavailable extends IllegalArgumentException {
        public KnowledgeUnavailable(String detail) { super(detail); }
    }

    public static List<SurfaceAnchor> path(FrontierWorldState state, ResourceSiteHarvestJob job) {
        Objects.requireNonNull(state, "known field navigation state");
        Objects.requireNonNull(job, "known field navigation job");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING
                || lifecycle.activeWork().filter(job::equals).isEmpty())
            throw new IllegalArgumentException("known field navigation has no current job owner");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("known field navigation has no living worker");
        ResourceSite site = Objects.requireNonNull(state.resourceSite(job.siteId()), "known field site");
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        if (goal.layoutRevision() != cycle.layout().revision()
                || !goal.jobId().equals(job.id()) || !goal.workerId().equals(job.workerId()))
            throw new IllegalArgumentException("known field navigation has a stale semantic goal");
        if (goal.kind() == ResourceSiteHarvestGoal.Kind.WORK_CELL) {
            ResourceFieldLayout.CellId cellId = goal.cellId().orElseThrow();
            ResourceFieldCycle.CellState cell = cycle.cell(cellId);
            if (cycle.pendingPlayerBreaks().containsKey(cellId)
                    || cell.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                    || cell.soil() == ResourceFieldCycle.Soil.OBSTRUCTED)
                throw new KnowledgeUnavailable("known field work goal awaits exact obstruction resolution");
        }
        SurfaceAnchor start = actor.supportingSurface();
        var surveyed = ResourceSiteHarvestKnownGeometry.surveyedSupports(state.bootstrap(), site);
        SettlementDepotServicePort port = ResourceSiteHarvestGoal.depotPort(state, job);
        SurfaceAnchor knownAtStart = surveyed.at(start.x(), start.z());
        if (!state.bootstrap().bounds().contains(start.support())
                // HOT may hand back a physically observed support one level above or
                // below the immutable survey. The bounded route search still has to
                // find a legal first edge from that exact body; accepting this start
                // does not synthesize a new support or semantic arrival.
                || Math.abs(start.y() - knownAtStart.y()) > 1
                    && !port.ownedAccessSurfaces().contains(start))
            throw new KnowledgeUnavailable("field worker body is not on retained known support");
        Set<BlockPosition> occupied = ResourceSiteHarvestKnownGeometry.occupiedBodies(state.bootstrap(), site);
        for (ResourceFieldLayout.Cell cell : cycle.layout().cells()) {
            ResourceFieldCycle.CellState condition = cycle.cell(cell.id());
            if (condition.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                    || condition.soil() == ResourceFieldCycle.Soil.OBSTRUCTED)
                occupied.add(cell.soil().support());
            if (condition.workAccessBlocked())
                occupied.add(cell.workstation().support().offset(0, 2, 0));
        }
        // These stations are retained infrastructure, including when a successor
        // begins at the exact port where its predecessor deposited a full batch.
        for (SurfaceAnchor surface : port.ownedAccessSurfaces()) {
            occupied.remove(surface.support());
            occupied.remove(surface.support().offset(0, 1, 0));
            occupied.remove(surface.support().offset(0, 2, 0));
        }
        if (!start.equals(knownAtStart) && !port.ownedAccessSurfaces().contains(start)) {
            // The exact retained body is a stronger predecessor than the immutable
            // occupancy approximation at this one off-survey column. A HOT detour
            // can stand on a newly observed support beside the field; clear only
            // that footprint, never the neighbouring route or destination.
            occupied.remove(start.support());
            occupied.remove(start.support().offset(0, 1, 0));
            occupied.remove(start.support().offset(0, 2, 0));
        } else if (occupied.contains(start.support()) || occupied.contains(start.support().offset(0, 1, 0))
                || occupied.contains(start.support().offset(0, 2, 0))) {
            throw new KnowledgeUnavailable("field worker's known support is no longer traversable");
        }
        try {
            return KnownPedestrianNavigation.route(state.bootstrap(), start, goal.movementOrder(), occupied, surveyed);
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            throw new KnowledgeUnavailable("field goal has no path in retained known geometry: " + goal.kind());
        }
    }
}
