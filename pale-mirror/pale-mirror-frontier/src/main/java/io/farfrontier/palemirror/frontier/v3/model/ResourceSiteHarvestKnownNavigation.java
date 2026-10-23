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
        public boolean planning() {
            return status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING;
        }
        public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status status() {
            return getCause() instanceof KnownPedestrianNavigation.RouteUnavailable unavailable ? unavailable.status()
                    : io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH;
        }
    }

    /** Scheduler eligibility and diagnostics consume retained evidence without starting a search. */
    public static List<SurfaceAnchor> retainedPath(FrontierWorldState state, ResourceSiteHarvestJob job) {
        try {
            var context = context(state, job);
            return context.knowledge().retainedPath(context.start(), context.goal().movementOrder());
        }
        catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            throw new KnowledgeUnavailable("field route has no retained calculation: " + unavailable.getMessage(), unavailable);
        }
    }
    public static List<SurfaceAnchor> plannedPath(FrontierWorldState state, ResourceSiteHarvestJob job) {
        try {
            Context context = context(state, job);
            return context.knowledge().plannedPath(context.start(), context.goal().movementOrder());
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            throw new KnowledgeUnavailable("field goal has no path in retained known geometry: "
                    + job.id().value() + ": " + unavailable.getMessage(), unavailable);
        }
    }

    /** Check the accepted producer result, not the route a second search happens to prefer. */
    public static void requireAcceptedDestination(FrontierWorldState state, ResourceSiteHarvestJob job,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> receipt) {
        requireAccepted(context(state, job), receipt);
    }
    public static void requireAcceptedStorageReplacement(FrontierWorldState state, ResourceSiteHarvestJob job,
            ResourceSiteHarvestJob replacement,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> receipt) {
        if (!job.retargetStorage(replacement.outputSlot(), replacement.intentId()).equals(replacement))
            throw new IllegalArgumentException("field storage route has a foreign successor");
        requireAccepted(context(state, job, true, replacement), receipt);
    }
    private static void requireAccepted(Context context,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> receipt) {
        if (receipt.isEmpty()) {
            if (!context.goal().arrivedAt(context.start()))
                throw new IllegalArgumentException("field arrival lacks a route or current goal station");
            context.knowledge().requireRoute(List.of(context.start()));
            return;
        }
        var route = receipt.orElseThrow().route();
        if (!route.getFirst().equals(context.start()) || !context.goal().arrivedAt(route.getLast()))
            throw new IllegalArgumentException("field route has a stale departure or foreign goal");
        context.knowledge().requireRoute(route);
    }

    private record Context(SurfaceAnchor start, ResourceSiteHarvestGoal goal, KnownPedestrianRouteKnowledge knowledge) { }

    private static Context context(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return context(state, job, false, job);
    }
    private static Context context(FrontierWorldState state, ResourceSiteHarvestJob job, boolean depotGoal,
            ResourceSiteHarvestJob routingJob) {
        Objects.requireNonNull(state, "known field navigation state");
        Objects.requireNonNull(job, "known field navigation job");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING
                || lifecycle.harvestJob(job.id()).filter(job::equals).isEmpty())
            throw new IllegalArgumentException("known field navigation has no current job owner");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("known field navigation has no living worker");
        ResourceSite site = Objects.requireNonNull(state.resourceSite(job.siteId()), "known field site");
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        ResourceSiteHarvestGoal goal = depotGoal ? ResourceSiteHarvestGoal.forSlot(state, routingJob, cycle.layout().cells().size())
                : ResourceSiteHarvestGoal.current(state, job);
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
        ResourceStorageAccess port = depotGoal ? ResourceSiteHarvestGoal.depotPort(state, routingJob)
                : ResourceSiteHarvestGoal.servicePort(state, routingJob);
        return new Context(start, goal, KnownPedestrianRouteKnowledge.forField(state, site, cycle, port, start).forActor(state, job.workerId()));
    }
}
