package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Optional;

/** Field owner binds the shared labour clock to its exact operation and current worker. */
public final class ResourceSiteHarvestWorkProcess {
    private ResourceSiteHarvestWorkProcess() { }
    public static ResourceSiteHarvestWorkChanged change(FrontierWorldState state, ResourceSiteHarvestJob job,
            long tick, boolean run, ScheduledAction binding, Optional<SceneLeaseId> lease) {
        var cycle = state.resourceSites().cycle(job.siteId());
        return new ResourceSiteHarvestWorkChanged(job.siteId(), job.id(), job.workerId(), cycle.epoch(),
                cycle.layout().revision(), cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id(),
                ResourceSiteHarvestLabour.operation(state, job), tick, job.progress().work(),
                ResourceSiteHarvestLabour.next(state, job, tick, run), binding.id(), binding.dueAt().ticks(), lease);
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestWorkChanged changed) {
        var lifecycle = state.resourceSites().site(subject);
        var job = lifecycle.harvestJob(changed.jobId()).orElseThrow();
        var field = state.resourceSites().cycle(subject);
        if (!subject.equals(changed.siteId()) || !job.id().equals(changed.jobId()) || !job.workerId().equals(changed.workerId())
                || job.progress().complete() || job.returningForBatch() || job.progress().hasPendingCrop()
                || state.resourceSites().hasPendingWorldChange(subject)
                || field.epoch() != changed.epoch() || field.layout().revision() != changed.layoutRevision()
                || !field.layout().cells().get(job.progress().nextCropSlotIndex()).id().equals(changed.cellId())
                || field.pendingPlayerBreaks().containsKey(changed.cellId())
                || !job.progress().work().equals(changed.previous())
                || ResourceSiteHarvestLabour.operation(state, job) != changed.operation())
            throw new IllegalArgumentException("labour transition has stale owner, cell, operation or predecessor");
        ResourceSiteHarvestProcess.requireContinuationBinding(job, new ScheduledAction(changed.scheduleId(),
                new SimInstant(changed.dueAt()), 0, subject, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1));
        if (changed.hotLeaseId().isPresent())
            FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, changed.hotLeaseId().orElseThrow());
        else if (FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                || !ActorExecutionCoordinator.coldAvailable(state, job.workerId()))
            throw new IllegalArgumentException("COLD labour cannot bypass physical worker authority");
        if (!changed.next().equals(ResourceSiteHarvestLabour.next(state, job, changed.atTick(), changed.next().running())))
            throw new IllegalArgumentException("labour transition changes its admitted rate or work amount");
        return state.withResourceSites(state.resourceSites().replace(lifecycle.withHarvestLabour(job, changed.next())));
    }
    public static List<ProposedEvent> events(FrontierWorldState state, ResourceSiteHarvestWorkChanged changed,
                                            ScheduledAction binding) {
        reduce(state, changed.siteId(), changed);
        var result = new java.util.ArrayList<ProposedEvent>();
        result.add(new ProposedEvent(changed.siteId(), changed));
        if (changed.next().running()) result.add(new ProposedEvent(changed.siteId(), new ScheduleEffect.Rescheduled(binding.id(),
                ResourceSiteHarvestProcess.coldProgress(state.resourceSites().site(changed.siteId()).harvestJob(changed.jobId()).orElseThrow(), changed.next().activeUntilTick()))));
        return List.copyOf(result);
    }
}
