package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourceFieldCellObservedTest {
    @Test void observedCropLossBeforePhysicalHarvestEffectRetainsFarmerAndReplansSameCell() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        SubjectId site = hot.site();
        FrontierWorldState state = hot.state();
        ResourceSiteHarvestJob job = hot.job();
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            state = ResourceSiteHarvestProcess.reduceHotGoalArrived(state, site,
                    new ResourceSiteHarvestHotGoalArrived(job.id(), hot.lease().id(), job.workerId(),
                            goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), goal.representative().standingBody()));
        state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site,
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex()));
        ResourceFieldCycle cycle = state.resourceSites().cycle(site);
        ResourceFieldLayout.CellId cell = cycle.layout().cells().get(job.progress().completedCropSlots()).id();
        var mature = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cell));
        var bare = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.ABSENT, 0);
        var observed = new ResourceFieldCellObserved(site, cycle.epoch(), cycle.layout().revision(), cell,
                mature, bare, ResourceFieldCellObserved.Change.CROP_REMOVED, ResourceFieldCellObserved.Source.WORLD,
                "world:prepared-crop-lost");
        FrontierWorldState held = ResourceSiteProcess.reduceWorldChangeHeld(state, site, new ResourceFieldWorldChangeHeld(observed));
        assertTrue(held.resourceSites().hasPendingWorldChange(site));
        FrontierWorldState changed = ResourceSiteProcess.reduceCellObserved(held, site, observed);
        ResourceSiteHarvestJob retained = (ResourceSiteHarvestJob) changed.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(job.id(), retained.id());
        assertEquals(job.workerId(), retained.workerId());
        assertEquals(job.progress().completedCropSlots(), retained.progress().completedCropSlots());
        assertFalse(retained.progress().hasPendingCrop());
        assertEquals(ResourceFieldCycle.WorkOutcome.PLANTED, changed.resourceSites().cycle(site).expectedWorkOutcome(cell));
        assertEquals(state.actorLocations().get(job.workerId()), changed.actorLocations().get(job.workerId()));
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
    }

    @Test void declaredCropLossAndSoilDamageStayLocalAcrossSnapshotRecovery() {
        FrontierWorldState ready = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceFieldCycle field = ready.resourceSites().cycle(site);
        ResourceFieldLayout.CellId cell = field.layout().cells().getFirst().id();
        ResourceFieldLayout.CellId neighbor = field.layout().cells().get(1).id();
        var mature = ResourceFieldPhysicalSurface.Condition.of(field.cell(cell));
        var prepared = new ResourceFieldPlayerBreakPrepared(site, field.epoch(), field.layout().revision(), cell, mature,
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000123"), "player:test-break-1");
        assertEquals(prepared, FrontierWorldRuntimeDefinition.payloadCodecs().decode(prepared.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(prepared)));
        FrontierWorldState pending = ResourceSiteProcess.reducePlayerBreakPrepared(ready, site, prepared);
        pending = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(pending));
        assertEquals(prepared.actionId(), pending.resourceSites().cycle(site).pendingPlayerBreaks().get(cell).actionId());
        var bare = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.ABSENT, 0);
        var removed = new ResourceFieldCellObserved(site, field.epoch(), field.layout().revision(), cell, mature, bare,
                ResourceFieldCellObserved.Change.CROP_REMOVED, ResourceFieldCellObserved.Source.PLAYER, "player:test-break-1");
        assertEquals(removed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(removed.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(removed)));
        FrontierWorldState changed = ResourceSiteProcess.reduceCellObserved(pending, site, removed);
        assertEquals(ResourceSitePhase.READY, changed.resourceSites().site(site).phase());
        assertEquals(ResourceFieldCycle.Crop.ABSENT, changed.resourceSites().cycle(site).cell(cell).crop());
        assertEquals(ResourceFieldCycle.Crop.MATURE, changed.resourceSites().cycle(site).cell(neighbor).crop());
        assertEquals(0, changed.resourceSites().cycle(site).harvestedCount());
        assertEquals(0, changed.resourceSites().cycle(site).pendingPlayerBreaks().size());
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(changed, site, removed),
                "a repeated physical observation cannot remove the same crop twice");

        var dirt = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.DIRT, ResourceFieldCycle.Crop.ABSENT, 0);
        var soilChanged = new ResourceFieldCellObserved(site, field.epoch(), field.layout().revision(), cell, bare, dirt,
                ResourceFieldCellObserved.Change.SOIL_BECAME_DIRT, ResourceFieldCellObserved.Source.WORLD, "world:test-soil-change-1");
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(changed, site, soilChanged),
                "a physical-looking world result without its canonical hold cannot advance the field");
        var held = new ResourceFieldWorldChangeHeld(soilChanged);
        assertEquals(held, FrontierWorldRuntimeDefinition.payloadCodecs().decode(held.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(held)));
        FrontierWorldState waiting = ResourceSiteProcess.reduceWorldChangeHeld(changed, site, held);
        waiting = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(waiting));
        assertEquals(soilChanged, waiting.resourceSites().pendingWorldChange(site));
        assertTrue(FrontierWorldRuntimeDefinition.scheduledHeld(waiting,
                growthProbe(site)));
        SubjectId other = new SubjectId("site:2-wheat-field");
        assertFalse(FrontierWorldRuntimeDefinition.scheduledHeld(waiting,
                growthProbe(other)),
                "one unresolved field cannot hold an unrelated settlement's growth");
        FrontierWorldState applied = ResourceSiteProcess.reduceCellObserved(waiting, site, soilChanged);
        assertEquals(soilChanged, applied.resourceSites().pendingWorldChange(site),
                "WAL acceptance alone cannot retire an unacknowledged physical claim");
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceWorldChangeAcknowledged(
                applied, site, new ResourceFieldWorldChangeAcknowledged(soilChanged, bare)),
                "a physical predecessor cannot release an already accepted changed cell");
        var acknowledged = new ResourceFieldWorldChangeAcknowledged(soilChanged, dirt);
        assertEquals(acknowledged, FrontierWorldRuntimeDefinition.payloadCodecs().decode(acknowledged.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(acknowledged)));
        FrontierWorldState damaged = ResourceSiteProcess.reduceWorldChangeAcknowledged(applied, site, acknowledged);
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(damaged));
        assertEquals(ResourceFieldCycle.Soil.DIRT, recovered.resourceSites().cycle(site).cell(cell).soil());
        assertNull(recovered.resourceSites().pendingWorldChange(site));
        assertEquals(ResourceFieldCycle.Crop.MATURE, recovered.resourceSites().cycle(site).cell(neighbor).crop());
        assertEquals(ready.resourceSites().site(site), recovered.resourceSites().site(site));
    }

    private static io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction growthProbe(SubjectId site) {
        return new io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction(
                new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:field-hold-probe-" + site.value().replace(':', '-')),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(100), 0, site,
                "frontier.resource_site.growth", 1);
    }

    @Test void savedWorldWitnessWithRevertedBlockClosesItsExactHoldWithoutChangingTheCell() {
        FrontierWorldState ready = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceFieldCycle field = ready.resourceSites().cycle(site);
        ResourceFieldLayout.CellId cell = field.layout().cells().getFirst().id();
        var before = ResourceFieldPhysicalSurface.Condition.of(field.cell(cell));
        var after = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var proposed = new ResourceFieldCellObserved(site, field.epoch(), field.layout().revision(), cell,
                before, after, ResourceFieldCellObserved.Change.CROP_REMOVED,
                ResourceFieldCellObserved.Source.WORLD, "world:crash-before-chunk-save");
        FrontierWorldState held = ResourceSiteProcess.reduceWorldChangeHeld(ready, site,
                new ResourceFieldWorldChangeHeld(proposed));
        var acknowledged = new ResourceFieldWorldChangeAcknowledged(proposed, before);
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceWorldChangeAcknowledged(
                held, site, new ResourceFieldWorldChangeAcknowledged(proposed, after)),
                "an unaccepted physical successor cannot release the held canonical predecessor");
        FrontierWorldState closed = ResourceSiteProcess.reduceWorldChangeAcknowledged(held, site, acknowledged);
        assertNull(closed.resourceSites().pendingWorldChange(site));
        assertEquals(field, closed.resourceSites().cycle(site));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(closed, site, proposed));
    }

    @Test void staleOrUnprovedCellObservationCannotChangeCanonicalField() {
        FrontierWorldState ready = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceFieldCycle field = ready.resourceSites().cycle(site);
        ResourceFieldLayout.CellId cell = field.layout().cells().getFirst().id();
        var mature = ResourceFieldPhysicalSurface.Condition.of(field.cell(cell));
        var bare = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.ABSENT, 0);
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldCellObserved(site, field.epoch(), field.layout().revision(), cell,
                mature, mature, ResourceFieldCellObserved.Change.CROP_REMOVED,
                ResourceFieldCellObserved.Source.PLAYER, "player:cancelled-click"),
                "an unchanged postcondition cannot declare crop loss");
        var stale = new ResourceFieldCellObserved(site, field.epoch(), field.layout().revision() + 1, cell, mature, bare,
                ResourceFieldCellObserved.Change.CROP_REMOVED, ResourceFieldCellObserved.Source.PLAYER, "player:stale");
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(ready, site, stale));
        var staleEpoch = new ResourceFieldCellObserved(site, field.epoch() + 1, field.layout().revision(), cell, mature,
                new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.DIRT, ResourceFieldCycle.Crop.ABSENT, 0),
                ResourceFieldCellObserved.Change.SOIL_BECAME_DIRT, ResourceFieldCellObserved.Source.WORLD, "world:other-epoch");
        assertEquals(staleEpoch, FrontierWorldRuntimeDefinition.payloadCodecs().decode(staleEpoch.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(staleEpoch)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(ready, site, staleEpoch),
                "a matching cell and predecessor from another growth epoch cannot damage this cycle");
        var stalePermission = new ResourceFieldPlayerBreakPrepared(site, field.epoch() + 1, field.layout().revision(),
                cell, mature, java.util.UUID.fromString("00000000-0000-0000-0000-000000000125"), "player:other-epoch");
        assertEquals(stalePermission, FrontierWorldRuntimeDefinition.payloadCodecs().decode(stalePermission.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(stalePermission)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reducePlayerBreakPrepared(ready, site, stalePermission),
                "a break permission from another growth epoch cannot be installed");
        var wrongPrior = new ResourceFieldCellObserved(site, field.epoch(), field.layout().revision(), cell,
                new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 6), bare,
                ResourceFieldCellObserved.Change.CROP_REMOVED, ResourceFieldCellObserved.Source.PLAYER, "player:wrong-prior");
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(ready, site, wrongPrior));
        var valid = new ResourceFieldCellObserved(site, field.epoch(), field.layout().revision(), cell, mature, bare,
                ResourceFieldCellObserved.Change.CROP_REMOVED, ResourceFieldCellObserved.Source.PLAYER, "player:valid");
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(ready, site, valid),
                "a player postcondition without a durable prepared action is inadmissible");
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(ready,
                new SubjectId("site:2-wheat-field"), valid));
    }

    @Test void cancelledPlayerBreakClosesOnlyItsExactPermissionWithoutLosingTheCrop() {
        FrontierWorldState ready = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceFieldCycle field = ready.resourceSites().cycle(site);
        ResourceFieldLayout.CellId cell = field.layout().cells().getFirst().id();
        var mature = ResourceFieldPhysicalSurface.Condition.of(field.cell(cell));
        var prepared = new ResourceFieldPlayerBreakPrepared(site, field.epoch(), field.layout().revision(), cell, mature,
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000124"), "player:cancelled");
        FrontierWorldState pending = ResourceSiteProcess.reducePlayerBreakPrepared(ready, site, prepared);
        var unchanged = new ResourceFieldCellObserved(site, field.epoch(), field.layout().revision(), cell, mature, mature,
                ResourceFieldCellObserved.Change.UNCHANGED, ResourceFieldCellObserved.Source.PLAYER, prepared.actionId());
        FrontierWorldState closed = ResourceSiteProcess.reduceCellObserved(pending, site, unchanged);
        assertEquals(field, closed.resourceSites().cycle(site));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(closed, site, unchanged));
    }
}
