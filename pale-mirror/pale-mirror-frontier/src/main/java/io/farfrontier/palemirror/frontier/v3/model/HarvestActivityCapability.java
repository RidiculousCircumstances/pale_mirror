package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Field owner supplies its exact checkpoint and preserves work/cargo through interruption. */
final class HarvestActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.FIELD_HARVEST; }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ActorActivityBodyCheckpoint.usesActorLocation(); }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public ActorActivityResumption resumptionReference() { return ActorActivityResumption.noRetainedExecutionReference(); }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> new ActorActivityDeath.Acknowledgement(state, execution,
                acknowledgeWorkerDeath(state, job(state, execution), tick), ActorActivityDeath.Disposition.RETAIN_CAUSAL_OWNER));
    }
    /** Current and paused claims enter the same owning callback; the scene is not the owner. */
    private static FrontierWorldStateUpdate acknowledgeWorkerDeath(FrontierWorldState state, ResourceSiteHarvestJob job, long tick) {
        var lifecycle = state.resourceSites().site(job.siteId());
        var site = state.resourceSite(job.siteId());
        if (job.progress().work().filter(WorkProgress::running).isPresent())
            lifecycle = lifecycle.withHarvestLabour(job, job.progress().work().orElseThrow().pause(tick));
        var conflict = new ResourceSiteConflictObserved(job.siteId(), site.cropSlots().getFirst(),
                ResourceSiteDiagnosticProducer.WORKER_DIED);
        var sites = state.resourceSites().replace(lifecycle.conflicted(ResourceSiteConflictDisposition.terminal(
                site.cropSlots().getFirst(), ResourceSiteConflictReason.WORKER_DIED,
                ResourceSiteConflictIncidents.first(lifecycle, conflict))));
        var intent = state.physicalIntents().get(job.intentId());
        if (intent == null) throw new IllegalArgumentException("dead harvest worker has no exact physical intent");
        var intents = new java.util.LinkedHashMap<>(state.physicalIntents());
        intents.put(intent.id(), intent.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.RESOURCE_SITE_HARVEST.stamp(intent)));
        return FrontierWorldStateUpdate.begin().resourceSites(sites)
                .strategicPlans(state.strategicPlans().transitionTask(job.taskId(), StrategicTaskStatus.BLOCKED))
                .physicalIntents(intents);
    }
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
    @Override public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        var ordinary = checkpoint(state, execution);
        if (!ordinary.ready()) return ordinary;
        var job = job(state, execution);
        return job.progress().work().filter(WorkProgress::running).isPresent()
                ? new ActorActivityCheckpoint(state, execution, Optional.of(new ActorActivityCheckpoint.Wait(
                        ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, job.id()))) : ordinary;
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        var job = job(state, execution);
        return lease.goal() == AmbientGoalKind.WORK
                && lease.goalBody().equals(state.actorLocations().get(job.workerId()).body());
    }
    static ResidentWorkYield.Status checkpointStatus(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return job.progress().hasPendingPhysicalWork() || state.resourceSites().harvestMutationPending(job)
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
