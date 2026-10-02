package io.farfrontier.palemirror.frontier.v3.model;

/** Field-owned labour policy. General statistics/progress never inspect field jobs. */
public final class ResourceSiteHarvestLabour {
    private ResourceSiteHarvestLabour() { }
    public static WorkOperation operation(FrontierWorldState state, ResourceSiteHarvestJob job) {
        var cycle = state.resourceSites().cycle(job.siteId());
        var cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        return switch (cycle.expectedWorkOutcome(cell)) {
            case HARVESTED -> WorkOperation.HARVEST;
            case PLANTED -> WorkOperation.SOW;
            case TILLED_AND_PLANTED -> WorkOperation.TILL_AND_SOW;
            case SKIPPED_BLOCKED, SKIPPED_IMMATURE -> throw new IllegalArgumentException("excluded cell has no labour operation");
        };
    }
    public static WorkProgress next(FrontierWorldState state, ResourceSiteHarvestJob job, long tick, boolean run) {
        var definition = state.bootstrap().ruleset().workCatalog().require(
                state.resourceSite(job.siteId()).kind().resourceKind(), operation(state, job));
        var prior = job.progress().work().orElseGet(() -> WorkProgress.pending(definition.workUnits(), tick));
        if (prior.requiredMilliWork() != Math.multiplyExact(definition.workUnits(), 1_000L))
            throw new IllegalArgumentException("retained labour belongs to a different operation");
        if (!run) return prior.pause(tick);
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job) || job.navigationBlock().isPresent()
                || !ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.workerId(), tick))
            throw new IllegalArgumentException("labour start needs a working farmer at the selected cell");
        var resident = state.humanPopulation().resident(job.workerId());
        long until = state.humanPopulation().schedule(resident.settlementId()).nextWindowBoundaryAfter(tick);
        var nutrition = state.humanPopulation().nutrition(resident.id()).accrueThrough(tick,
                state.bootstrap().ruleset().residentLife(), resident.characteristics().effectiveMetabolismPermille(tick));
        long threshold = nutrition.nextThresholdTick(state.bootstrap().ruleset().residentLife(),
                resident.characteristics().effectiveMetabolismPermille(tick));
        // An admitted interval cannot secretly accrue across a known personal/day boundary.
        if (threshold > tick) until = Math.min(until, threshold);
        return prior.resume(tick, ResidentWorkStatistics.speedPermille(resident, definition,
                state.bootstrap().ruleset().workCatalog()), until);
    }
    public static FrontierWorldState pause(FrontierWorldState state, HumanAssignment assignment, long tick) {
        return pauseJob(state, assignedJob(state, assignment), tick);
    }
    public static java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> workStatsChanged(
            FrontierWorldState state, HumanAssignment assignment, long tick) {
        var job = assignedJob(state, assignment);
        var next = ResourceSiteHarvestContinuation.at(job,
                Math.addExact(tick, 1));
        return java.util.List.of(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(job.siteId(),
                new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled(next.id(), next)));
    }
    private static ResourceSiteHarvestJob assignedJob(FrontierWorldState state, HumanAssignment assignment) {
        if (assignment.kind() != HumanAssignmentKind.FIELD_HARVEST)
            throw new IllegalArgumentException("field labour suspension has a foreign assignment");
        // The assignment already declares its job owner; site discovery is constrained to that owner's exact retained job.
        var lifecycle = state.resourceSites().sites().values().stream()
                .filter(site -> site.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                        .map(ResourceSiteHarvestJob.class::cast).filter(job -> job.id().equals(assignment.ownerId().orElseThrow())).isPresent())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("field labour owner lost its job"));
        var job = (ResourceSiteHarvestJob) lifecycle.activeWork().orElseThrow();
        if (!job.workerId().equals(assignment.residentId())) throw new IllegalArgumentException("foreign field worker");
        return job;
    }
    public static FrontierWorldState pauseJob(FrontierWorldState state, ResourceSiteHarvestJob job, long tick) {
        if (job.progress().work().isEmpty() || !job.progress().work().orElseThrow().running()) return state;
        var work = job.progress().work().orElseThrow().pause(tick);
        return state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(job.siteId()).withHarvestLabour(job, work)));
    }
}
