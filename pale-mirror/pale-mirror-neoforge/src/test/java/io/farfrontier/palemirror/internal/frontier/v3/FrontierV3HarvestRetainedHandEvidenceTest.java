package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3HarvestRetainedHandEvidenceTest {
    @Test void recoveryNeedsTheExactLastAcceptedWorkAndBodyNotMerelyEqualWheatCounts() {
        var base = FrontierV3FixtureCatalog.resourceSiteHarvestConfiguration(new WorldId("frontier:retained-hand"), 421L);
        var state = base.initialState();
        var site = new SubjectId("site:1-wheat-field");
        var job = state.resourceSites().site(site).harvestJobs().values().iterator().next();
        var cycle = state.resourceSites().cycle(site);
        var cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        var progress = job.progress().prepareNextCrop().confirmPreparedCrop();
        var completed = job.withProgress(job.progress().prepareNextCrop()).withConfirmedCrop(progress, ResourceFieldCycle.WorkOutcome.HARVESTED).bindTarget(
                cycle.worked(cell, ResourceFieldCycle.WorkOutcome.HARVESTED));
        var address = new PhysicalStackAddress.ActorHand(job.workerId(),
                SceneLease.deterministicEntityId(base.worldId(), job.workerId()));
        var receipt = new ResourceSiteHarvestProgressed(site, cycle.epoch(), job.id(), progress.completedCropSlots(),
                cycle.layout().revision(), cell, job.target().generation(), ResourceFieldCycle.WorkOutcome.HARVESTED,
                new ScheduleId("schedule:retained-hand"), 1L,
                Optional.of(new ResourceSiteHarvestProgressed.HandObservation(address, 17L, 1)));
        var observed = new FungiblePhysicalObservation.Stack(address, "minecraft:wheat", 1);
        assertTrue(FrontierV3HarvestRetainedHandEvidence.matches(completed, cycle, receipt, observed));
        assertFalse(FrontierV3HarvestRetainedHandEvidence.matches(job, cycle, receipt, observed),
                "a native receipt not yet accepted canonically cannot repair inventory");
        var wrongBody = new PhysicalStackAddress.ActorHand(job.workerId(), java.util.UUID.fromString("00000000-0000-0000-0000-000000000007"));
        assertFalse(FrontierV3HarvestRetainedHandEvidence.matches(completed, cycle, receipt,
                new FungiblePhysicalObservation.Stack(wrongBody, "minecraft:wheat", 1)));
        assertFalse(FrontierV3HarvestRetainedHandEvidence.matches(completed, cycle, receipt,
                new FungiblePhysicalObservation.Stack(address, "minecraft:wheat", 2)));
        var old = new ResourceSiteHarvestProgressed(site, cycle.epoch(), job.id(), progress.completedCropSlots() + 1,
                cycle.layout().revision(), cell, job.target().generation(), receipt.outcome(), receipt.coldScheduleId(),
                receipt.coldDueAt(), receipt.observedHand());
        assertFalse(FrontierV3HarvestRetainedHandEvidence.matches(completed, cycle, old, observed),
                "another checkpoint with the same amount cannot stand in for the retained last effect");
    }
}
