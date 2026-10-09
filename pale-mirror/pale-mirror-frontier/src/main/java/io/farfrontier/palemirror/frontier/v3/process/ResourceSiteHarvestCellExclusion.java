package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Exact owner-bound exclusion of a selected cell without movement, physical effect or yield. */
final class ResourceSiteHarvestCellExclusion {
    private ResourceSiteHarvestCellExclusion() { }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject,
                                                       ResourceSiteHarvestCellSkip skipped) {
        if (!subject.equals(skipped.siteId()))
            throw new IllegalArgumentException("excluded field cell has a foreign site owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.harvestJob(skipped.jobId()).orElseThrow(
                        () -> new IllegalArgumentException("excluded field cell has no active farmer"));
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || !job.id().equals(skipped.jobId())
                || !job.workerId().equals(skipped.workerId()) || job.progress().complete()
                || job.progress().hasPendingCrop() || job.returningForBatch()
                || state.resourceSites().harvestMutationPending(job)
                || skipped.layoutRevision() != cycle.layout().revision())
            throw new IllegalArgumentException("excluded field cell has a stale or physically unresolved work boundary");
        ResourceFieldLayout.Cell selected = cycle.layout().cells().get(job.progress().nextCropSlotIndex());
        if (!skipped.cellIds().equals(List.of(selected.id()))
                || cycle.cell(selected.id()).accounted()
                || cycle.expectedWorkOutcome(selected.id()) != skipped.outcome()
                || cycle.pendingPlayerBreaks().containsKey(selected.id()))
            throw new IllegalArgumentException("excluded field skip lacks its exact selected no-effect outcome");
        if (job.navigationBlock().isPresent()
                && !job.navigationBlock().orElseThrow().target().equals(ResourceSiteHarvestGoal.current(state, job).representative()))
            throw new IllegalArgumentException("excluded field skip has a different retained movement goal");
        ResourceSiteHarvestProcess.requireContinuationBinding(job, new ScheduledAction(skipped.coldScheduleId(),
                new SimInstant(skipped.coldDueAt()), 0, subject, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1));
        if (skipped.hotLeaseId().isPresent()) {
            FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, skipped.hotLeaseId().orElseThrow());
        } else if (FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                || !ActorExecutionCoordinator.coldAvailable(state, job.workerId())) {
            throw new IllegalArgumentException("COLD excluded-cell continuation cannot bypass a physical farmer");
        }
        ResourceFieldCycle worked = cycle.worked(selected.id(), skipped.outcome());
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("excluded field skip has no retained worker body");
        int nextSelected = lifecycle.nextHarvestTarget(job, worked);
        ResourceSiteLifecycle advanced = lifecycle.skipSelectedHarvestCell(job, nextSelected, worked);
        return state.withResourceSites(state.resourceSites().replace(advanced, worked));
    }
}
