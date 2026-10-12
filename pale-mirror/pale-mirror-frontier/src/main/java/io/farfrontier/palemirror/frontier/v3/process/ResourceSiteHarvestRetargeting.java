package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;

/** Shared HOT/COLD selection boundary for a route-inaccessible area-work target. */
public final class ResourceSiteHarvestRetargeting {
    private ResourceSiteHarvestRetargeting() { }

    /** Producer-selected destination evidence; an absent leg means the actor is already there. */
    public record Selection(int slot, Optional<PedestrianRouteReceipt> plannedRoute) {
        public Selection { if (slot < 0) throw new IllegalArgumentException("negative alternate field slot");
            java.util.Objects.requireNonNull(plannedRoute); }
    }

    /** A failed target remains outstanding. Pending calculations propagate, never mean no alternative. */
    public static Optional<Selection> coldAlternative(FrontierWorldState state, ResourceSiteHarvestJob job) {
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        var selected = new java.util.ArrayList<Selection>(1);
        cycle.reachableWorkSlotAfter(job.progress().nextCropSlotIndex(), index -> {
            if (!lifecycle.targetAvailable(index, job.id())
                    || state.resourceSites().hasPendingCellMutation(job.siteId(), cycle.layout().cells().get(index).id())) return false;
            ResourceSiteHarvestJob candidate = job.retargetTo(index).bindTarget(cycle);
            FrontierWorldState projected = state.withResourceSites(state.resourceSites().replace(
                    lifecycle.retargetHarvestCell(job, index, cycle)));
            try {
                var route = ResourceSiteHarvestKnownNavigation.plannedPath(projected, candidate);
                selected.add(new Selection(index, route.size() < 2 ? Optional.empty()
                        : Optional.of(new PedestrianRouteReceipt(route))));
                return true;
            } catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable unavailable) {
                if (unavailable.planning()) throw unavailable; // Await this candidate; never fan out a field-sized search batch.
                return false;
            }
        });
        return selected.stream().findFirst();
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
                || state.resourceSites().harvestMutationPending(job)
                || retargeted.toSlot() >= cycle.layout().cells().size())
            throw new IllegalArgumentException("area work retarget has a stale or unresolved work boundary");
        ResourceFieldLayout.CellId target = cycle.layout().cells().get(retargeted.toSlot()).id();
        ResourceFieldCycle.CellState condition = cycle.cell(target);
        if (!lifecycle.targetAvailable(retargeted.toSlot(), job.id()) || condition.accounted() || condition.workAccessBlocked()
                || condition.crop() == ResourceFieldCycle.Crop.OBSTRUCTED || condition.crop() == ResourceFieldCycle.Crop.GROWING
                || state.resourceSites().hasPendingCellMutation(subject, target))
            throw new IllegalArgumentException("area work retarget has no eligible alternate cell");
        ResourceSiteHarvestPlanning.requireContinuationBinding(job, new ScheduledAction(retargeted.coldScheduleId(),
                new SimInstant(retargeted.coldDueAt()), 0, subject, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1));
        state.actorExecutions().requireCurrent(retargeted.executionId());
        var redirected = lifecycle.retargetHarvestCell(job, retargeted.toSlot(), cycle);
        var projected = state.withResourceSites(state.resourceSites().replace(redirected));
        if (retargeted.hotLeaseId().isPresent()) {
            FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, retargeted.hotLeaseId().orElseThrow());
        } else {
            if (!ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                    || FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job))
                throw new IllegalArgumentException("COLD area work retarget lacks an exact known alternative");
            ResourceSiteHarvestKnownNavigation.requireAcceptedDestination(projected,
                    redirected.harvestJob(job.id()).orElseThrow(), retargeted.plannedRoute());
        }
        return projected;
    }
}
