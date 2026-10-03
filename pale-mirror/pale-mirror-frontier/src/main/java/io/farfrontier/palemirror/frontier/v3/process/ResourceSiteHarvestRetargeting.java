package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.OptionalInt;

/** Shared HOT/COLD selection boundary for a route-inaccessible area-work target. */
public final class ResourceSiteHarvestRetargeting {
    private ResourceSiteHarvestRetargeting() { }

    /** Pure COLD alternative search; a failed target remains in the outstanding CellId pool. */
    public static OptionalInt coldReachableWorkTarget(FrontierWorldState state, ResourceSiteHarvestJob job) {
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        return cycle.reachableWorkSlotAfter(job.progress().nextCropSlotIndex(), index -> {
            if (!lifecycle.targetAvailable(index, job.id())) return false;
            ResourceSiteHarvestJob candidate = job.retargetTo(index).bindTarget(cycle);
            FrontierWorldState projected = state.withResourceSites(state.resourceSites().replace(
                    lifecycle.retargetHarvestCell(job, index, cycle)));
            try {
                ResourceSiteHarvestKnownNavigation.path(projected, candidate);
                return true;
            } catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable unavailable) {
                return false;
            }
        });
    }

    public static FrontierWorldState reduceTargetRetargeted(FrontierWorldState state, SubjectId subject,
                                                             ResourceSiteHarvestTargetRetargeted retargeted) {
        if (!subject.equals(retargeted.siteId()))
            throw new IllegalArgumentException("area work retarget has a foreign site owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.harvestJob(retargeted.jobId()).orElseThrow();
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || !job.id().equals(retargeted.jobId())
                || !job.workerId().equals(retargeted.workerId()) || job.progress().complete()
                || job.progress().hasPendingCrop() || job.returningForBatch()
                || job.navigationBlock().filter(block -> !block.reroutable()).isPresent()
                || retargeted.layoutRevision() != cycle.layout().revision()
                || retargeted.fromSlot() != job.progress().nextCropSlotIndex()
                || state.resourceSites().hasPendingWorldChange(subject)
                || retargeted.toSlot() >= cycle.layout().cells().size())
            throw new IllegalArgumentException("area work retarget has a stale or unresolved work boundary");
        ResourceFieldLayout.CellId target = cycle.layout().cells().get(retargeted.toSlot()).id();
        ResourceFieldCycle.CellState condition = cycle.cell(target);
        if (!lifecycle.targetAvailable(retargeted.toSlot(), job.id()) || condition.accounted() || condition.workAccessBlocked()
                || condition.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                || cycle.pendingPlayerBreaks().containsKey(target))
            throw new IllegalArgumentException("area work retarget has no eligible alternate cell");
        ResourceSiteHarvestPlanning.requireContinuationBinding(job, new ScheduledAction(retargeted.coldScheduleId(),
                new SimInstant(retargeted.coldDueAt()), 0, subject, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1));
        if (retargeted.hotLeaseId().isPresent()) {
            FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, retargeted.hotLeaseId().orElseThrow());
        } else {
            boolean currentUnavailable = false;
            try {
                ResourceSiteHarvestKnownNavigation.path(state, job);
            } catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable unavailable) {
                currentUnavailable = true;
            }
            if (!ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                    || !currentUnavailable
                    || !coldReachableWorkTarget(state, job).equals(OptionalInt.of(retargeted.toSlot())))
                throw new IllegalArgumentException("COLD area work retarget lacks an exact known alternative");
        }
        return state.withResourceSites(state.resourceSites().replace(
                lifecycle.retargetHarvestCell(job, retargeted.toSlot(), cycle)));
    }
}
