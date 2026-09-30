package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
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
        public KnowledgeUnavailable(String detail, Throwable cause) { super(detail, cause); }
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
        SettlementDepotServicePort port = ResourceSiteHarvestGoal.depotPort(state, job);
        try {
            return KnownPedestrianRouteKnowledge.forField(state, site, cycle, port, start)
                    .path(start, goal.movementOrder());
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            throw new KnowledgeUnavailable("field goal has no path in retained known geometry: "
                    + goal.kind() + ": " + unavailable.getMessage(), unavailable);
        }
    }
}
