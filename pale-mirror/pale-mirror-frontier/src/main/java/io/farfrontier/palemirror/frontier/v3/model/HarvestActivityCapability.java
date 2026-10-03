package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Field owner supplies its exact checkpoint and preserves work/cargo through interruption. */
final class HarvestActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.FIELD_HARVEST; }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("field work must retain its continuation or complete through its owner");
    }
    private ResourceSiteHarvestJob job(FrontierWorldState state, ActorExecutionId execution) {
        return job(state.resourceSites(), execution);
    }
    private static ResourceSiteHarvestJob job(ResourceSiteState sites, ActorExecutionId execution) {
        if (execution.activityKind() != ActorActivityKind.FIELD_HARVEST) throw new IllegalArgumentException("foreign field execution kind");
        var job = sites.sites().values().stream()
                .map(site -> site.harvestJob(execution.activityOwnerId())).flatMap(Optional::stream)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("field execution lost its exact job"));
        if (!job.workerId().equals(execution.actorId())) throw new IllegalArgumentException("field execution has a foreign worker");
        return job;
    }
    static void validateReferences(ResourceSiteState sites, ActorExecutionState executions) {
        executions.current(ActorActivityKind.FIELD_HARVEST).values().forEach(id -> job(sites, id));
        executions.suspended().stream().filter(id -> id.activityKind() == ActorActivityKind.FIELD_HARVEST)
                .forEach(id -> job(sites, id));
    }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) { job(state, execution); }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        var job = job(state, execution);
        var waiting = switch (checkpointStatus(state, job)) {
            case PENDING_PHYSICAL_EFFECT -> Optional.of(new ActorActivityCheckpoint.Wait(
                    ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, job.siteId()));
            case READY -> Optional.<ActorActivityCheckpoint.Wait>empty();
            default -> throw new IllegalArgumentException("field checkpoint has no declared UAE translation");
        };
        return new ActorActivityCheckpoint(state, execution, waiting);
    }
    static ResidentWorkYield.Status checkpointStatus(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return job.progress().hasPendingCrop() || state.resourceSites().hasPendingWorldChange(job.siteId())
                ? ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT : ResidentWorkYield.Status.READY;
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        return ResourceSiteHarvestLabour.pauseJob(state, job(state, execution), atTick);
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        var job = job(state, execution);
        // Resume grants work authority, not accrued labour or arrival. The field owner
        // starts a new interval only after revalidating the actual work cell and schedule.
        if (job.progress().work().filter(WorkProgress::running).isPresent())
            throw new IllegalArgumentException("suspended field retained a running labour interval");
        if (job.progress().work().filter(work -> work.evaluatedAtTick() > atTick).isPresent())
            throw new IllegalArgumentException("field resume cannot run its labour clock backwards");
        return state;
    }
}
