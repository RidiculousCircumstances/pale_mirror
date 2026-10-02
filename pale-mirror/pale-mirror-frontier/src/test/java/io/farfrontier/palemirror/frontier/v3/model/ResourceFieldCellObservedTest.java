package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
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
    @Test void externalGrowthUsesExactReceiptsAndPublishesFirstMatureCellWorkOpportunity() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var site = new SubjectId("site:1-wheat-field");
        var preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) preparation.get(1).payload());
        var cell = state.resourceSites().cycle(site).layout().cells().getFirst().id();
        for (int age : new int[] {3, 7}) {
            var cycle = state.resourceSites().cycle(site);
            var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cell));
            var after = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                    age == 7 ? ResourceFieldCycle.Crop.MATURE : ResourceFieldCycle.Crop.GROWING, age);
            var observation = new ResourceFieldCellObserved(site, cycle.epoch(), cycle.layout().revision(), cell,
                    before, after, ResourceFieldCellObserved.Change.CROP_GROWN, ResourceFieldCellObserved.Source.WORLD,
                    "world:bone-meal-" + age);
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(observation, codecs.decode(observation.type(), codecs.encode(observation)));
            var held = ResourceSiteProcess.reduceWorldChangeHeld(state, site, new ResourceFieldWorldChangeHeld(observation));
            assertEquals(held, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(held)));
            var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:external-growth-" + age);
            var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, id, state.bootstrap().worldId(),
                    io.farfrontier.palemirror.frontier.v3.api.Revision.ZERO, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(4_001),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), observation);
            var planned = org.junit.jupiter.api.Assertions.assertInstanceOf(
                    io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                    FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed())
                            .commandPlanner().plan(held, command));
            assertEquals(age == 7 ? 2 : 1, planned.events().size());
            state = ResourceSiteProcess.reduceCellObserved(held, site, observation);
            var changed = state;
            assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(changed, site, observation));
            state = ResourceSiteProcess.reduceWorldChangeAcknowledged(state, site,
                    new ResourceFieldWorldChangeAcknowledged(observation, after));
            assertFalse(state.resourceSites().hasPendingWorldChange(site));
            assertEquals(age, state.resourceSites().cycle(site).cell(cell).growthStage());
            assertEquals(0, state.resourceSites().cycle(site).harvestedCount());
        }
        assertEquals(ResourceSitePhase.READY, state.resourceSites().site(site).phase());
        assertEquals(ResourceFieldCycle.WorkOutcome.HARVESTED, state.resourceSites().cycle(site).expectedWorkOutcome(cell));
        var ripe = ResourceFieldPhysicalSurface.Condition.of(state.resourceSites().cycle(site).cell(cell));
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldCellObserved(site, 1, 1, cell,
                ripe, ripe, ResourceFieldCellObserved.Change.CROP_GROWN, ResourceFieldCellObserved.Source.WORLD, "world:no-growth"));
    }

    @Test void regrownAccountedCellPreservesOldYieldAndBecomesNewWorkOnlyInNextBatch() {
        var state = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        var cycle = state.resourceSites().cycle(new SubjectId("site:1-wheat-field"));
        var cell = cycle.layout().cells().getFirst().id();
        cycle = cycle.harvested(cell);
        var regrown = cycle.observedGrowth(cell, new ResourceFieldPhysicalSurface.Condition(
                ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.MATURE, 7));
        assertEquals(1, regrown.harvestedCount());
        assertTrue(regrown.cell(cell).accounted());
        var currentBatch = regrown;
        assertThrows(IllegalArgumentException.class, () -> currentBatch.expectedWorkOutcome(cell));
        for (var other : regrown.layout().cells()) if (!other.id().equals(cell)) regrown = regrown.harvested(other.id());
        var successor = regrown.nextEpoch();
        assertEquals(0, successor.harvestedCount());
        assertEquals(7, successor.cell(cell).growthStage());
        assertEquals(ResourceFieldCycle.WorkOutcome.HARVESTED, successor.expectedWorkOutcome(cell));
    }

    @Test void externalReplantCancelsPreparedCropAndChoosesAnotherCellWithoutYield() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var job = hot.job();
        var goal = ResourceSiteHarvestGoal.current(hot.state(), job);
        var state = hot.state();
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            state = ResourceSiteHarvestProcess.reduceHotGoalArrived(state, hot.site(),
                    new ResourceSiteHarvestHotGoalArrived(job.id(), hot.lease().id(), job.workerId(),
                            goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), goal.representative().standingBody()));
        state = ResourceSiteHarvestProcessTest.completeLabourHot(state, hot.site(), hot.lease().id());
        state = ResourceSiteHarvestProcess.reduceCropPrepared(state, hot.site(),
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex()));
        var cycle = state.resourceSites().cycle(hot.site());
        var id = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(id));
        var after = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 0);
        var observed = new ResourceFieldCellObserved(hot.site(), cycle.epoch(), cycle.layout().revision(), id,
                before, after, ResourceFieldCellObserved.Change.CROP_REPLANTED, ResourceFieldCellObserved.Source.WORLD,
                "world:external-harvest-replant");
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(observed, codecs.decode(observed.type(), codecs.encode(observed)));
        var held = ResourceSiteProcess.reduceWorldChangeHeld(state, hot.site(), new ResourceFieldWorldChangeHeld(observed));
        assertEquals(held, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(held)));
        var changed = ResourceSiteProcess.reduceCellObserved(held, hot.site(), observed);
        var retained = (ResourceSiteHarvestJob) changed.resourceSites().site(hot.site()).activeWork().orElseThrow();
        assertFalse(retained.progress().hasPendingCrop());
        assertTrue(changed.resourceSites().cycle(hot.site()).cell(id).accounted());
        assertEquals(ResourceFieldCycle.Crop.GROWING, changed.resourceSites().cycle(hot.site()).cell(id).crop());
        assertEquals(0, changed.resourceSites().cycle(hot.site()).harvestedCount());
        assertFalse(cycle.layout().cells().get(retained.progress().nextCropSlotIndex()).id().equals(id));
        assertEquals(state.actorLocations(), changed.actorLocations());
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(changed, hot.site(), observed));
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldCellObserved(hot.site(), cycle.epoch(),
                cycle.layout().revision(), id, after, after, ResourceFieldCellObserved.Change.CROP_REPLANTED,
                ResourceFieldCellObserved.Source.WORLD, "world:no-new-loss"));
    }
    @Test void playerBreakOfActiveTargetClosesOnlyThatCellAfterExactPostcondition() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        SubjectId site = hot.site();
        ResourceSiteHarvestJob job = hot.job();
        ResourceFieldCycle cycle = hot.state().resourceSites().cycle(site);
        var cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cell));
        var permission = new ResourceFieldPlayerBreakPrepared(site, cycle.epoch(), cycle.layout().revision(), cell,
                before, java.util.UUID.fromString("00000000-0000-0000-0000-000000000124"), "player:active-harvest-loss");
        FrontierWorldState pending = ResourceSiteProcess.reducePlayerBreakPrepared(hot.state(), site, permission);
        assertEquals(job.progress(), ((ResourceSiteHarvestJob) pending.resourceSites().site(site).activeWork().orElseThrow()).progress(),
                "permission alone cannot count a cancelled or not-yet-observed break");
        var after = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var observed = new ResourceFieldCellObserved(site, cycle.epoch(), cycle.layout().revision(), cell,
                before, after, ResourceFieldCellObserved.Change.CROP_REMOVED,
                ResourceFieldCellObserved.Source.PLAYER, permission.actionId());
        FrontierWorldState changed = ResourceSiteProcess.reduceCellObserved(pending, site, observed);
        ResourceSiteHarvestJob retained = (ResourceSiteHarvestJob) changed.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(ResourceSitePhase.HARVESTING, changed.resourceSites().site(site).phase());
        assertEquals(job.id(), retained.id());
        assertEquals(job.progress().completedCropSlots() + 1, retained.progress().completedCropSlots());
        assertEquals(0, changed.resourceSites().cycle(site).harvestedCount());
        assertTrue(changed.resourceSites().cycle(site).cell(cell).accounted());
        assertFalse(changed.resourceSites().cycle(site).cell(cell).yielded());
        assertEquals(0, changed.resourceSites().cycle(site).pendingPlayerBreaks().size());
        assertEquals(hot.state().actorLocations().get(job.workerId()), changed.actorLocations().get(job.workerId()));
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceCellObserved(changed, site, observed));
    }

    @Test void physicalWorkAccessObservationRetainsCropAndSelectsAnotherAreaCell() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        FrontierWorldState state = hot.state();
        SubjectId site = hot.site();
        ResourceSiteHarvestJob job = hot.job();
        ResourceFieldCycle cycle = state.resourceSites().cycle(site);
        var cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex());
        var head = cell.workstation().support().offset(0, 2, 0);
        var observed = new ResourceFieldWorkAccessObserved(site, cycle.epoch(), cycle.layout().revision(),
                cell.id(), head, true, "minecraft:stone", java.util.Optional.of(hot.lease().id()));
        assertEquals(observed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(observed.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(observed)));
        FrontierWorldState blocked = ResourceSiteProcess.reduceWorkAccessObserved(state, site, observed);
        assertEquals(ResourceFieldCycle.Crop.MATURE, blocked.resourceSites().cycle(site).cell(cell.id()).crop());
        assertEquals(0, blocked.resourceSites().cycle(site).harvestedCount());
        assertEquals(ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED,
                blocked.resourceSites().cycle(site).expectedWorkOutcome(cell.id()));
        assertEquals(blocked, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(blocked)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceWorkAccessObserved(blocked, site, observed));
    }

    @Test void observedCropLossBeforePhysicalHarvestEffectClosesCellAndRetargetsFarmer() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        SubjectId site = hot.site();
        FrontierWorldState state = hot.state();
        ResourceSiteHarvestJob job = hot.job();
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            state = ResourceSiteHarvestProcess.reduceHotGoalArrived(state, site,
                    new ResourceSiteHarvestHotGoalArrived(job.id(), hot.lease().id(), job.workerId(),
                            goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), goal.representative().standingBody()));
        state = ResourceSiteHarvestProcessTest.completeLabourHot(state, site, hot.lease().id());
        state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site,
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex()));
        ResourceFieldCycle cycle = state.resourceSites().cycle(site);
        ResourceFieldLayout.CellId cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
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
        assertEquals(job.progress().completedCropSlots() + 1, retained.progress().completedCropSlots());
        assertFalse(retained.progress().hasPendingCrop());
        assertTrue(changed.resourceSites().cycle(site).cell(cell).accounted());
        assertFalse(changed.resourceSites().cycle(site).cell(cell).yielded());
        assertEquals(0, changed.resourceSites().cycle(site).harvestedCount());
        assertFalse(changed.resourceSites().cycle(site).layout().cells().get(retained.progress().nextCropSlotIndex()).id().equals(cell));
        assertEquals(state.actorLocations().get(job.workerId()), changed.actorLocations().get(job.workerId()));
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
    }

    @Test void lossOfUnselectedCellKeepsCurrentGoalAndClosesItWithoutAVisit() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        SubjectId site = hot.site();
        ResourceSiteHarvestJob job = hot.job();
        ResourceFieldCycle cycle = hot.state().resourceSites().cycle(site);
        var other = cycle.layout().cells().get(1).id();
        var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(other));
        var after = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var observed = new ResourceFieldCellObserved(site, cycle.epoch(), cycle.layout().revision(), other,
                before, after, ResourceFieldCellObserved.Change.CROP_REMOVED,
                ResourceFieldCellObserved.Source.WORLD, "world:unselected-crop-lost");
        FrontierWorldState held = ResourceSiteProcess.reduceWorldChangeHeld(hot.state(), site,
                new ResourceFieldWorldChangeHeld(observed));
        FrontierWorldState changed = ResourceSiteProcess.reduceCellObserved(held, site, observed);
        ResourceSiteHarvestJob retained = (ResourceSiteHarvestJob) changed.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(job.progress().nextCropSlotIndex(), retained.progress().nextCropSlotIndex());
        assertEquals(1, retained.progress().completedCropSlots());
        assertTrue(changed.resourceSites().cycle(site).cell(other).accounted());
        assertEquals(0, changed.resourceSites().cycle(site).harvestedCount());
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
        ResourceFieldCycle obstructed = changed.resourceSites().cycle(site)
                .cropObstructed(cycle.layout().cells().get(retained.progress().nextCropSlotIndex()).id());
        FrontierWorldState withObstruction = changed.withResourceSites(changed.resourceSites().replace(
                changed.resourceSites().site(site), obstructed));
        var blocked = ResourceSiteHarvestProcess.blockedPrefix(obstructed, retained.progress().nextCropSlotIndex());
        assertEquals(1, blocked.size());
        int expectedNext = obstructed.skipBlocked(blocked.getFirst())
                .nextWorkSlotAfter(retained.progress().nextCropSlotIndex()).orElse(-1);
        assertEquals(expectedNext, ResourceSiteHarvestProcess.blockedPrefixContinuationGoal(
                withObstruction, retained, blocked).nextWorkSlot(),
                "a noncontiguous completed cell is not the next positional route target");
    }

    @Test void lossOfLastCellAfterEarlierBatchClosesWorkWithoutAnotherCropVisit() {
        var cold = FrontierResourceSiteHarvestFixture.createWithOneExtraCellAfterColdPart(
                new WorldId("frontier:last-cell-external-loss"), 125L);
        SubjectId site = cold.siteId();
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) cold.state().resourceSites().site(site)
                .activeWork().orElseThrow();
        ResourceFieldCycle cycle = cold.state().resourceSites().cycle(site);
        var last = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(last));
        var after = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var observed = new ResourceFieldCellObserved(site, cycle.epoch(), cycle.layout().revision(), last,
                before, after, ResourceFieldCellObserved.Change.CROP_REMOVED,
                ResourceFieldCellObserved.Source.WORLD, "world:last-crop-lost");
        FrontierWorldState held = ResourceSiteProcess.reduceWorldChangeHeld(cold.state(), site,
                new ResourceFieldWorldChangeHeld(observed));
        FrontierWorldState changed = ResourceSiteProcess.reduceCellObserved(held, site, observed);
        ResourceSiteHarvestJob retained = (ResourceSiteHarvestJob) changed.resourceSites().site(site).activeWork().orElseThrow();
        assertTrue(retained.progress().complete());
        assertEquals(job.progress().lastCompletedCropSlotIndex(), retained.progress().lastCompletedCropSlotIndex(),
                "external loss must not hide the preceding physical farmer-effect witness");
        assertEquals(64, changed.resourceSites().cycle(site).harvestedCount());
        assertEquals(0, retained.carriedYieldQuantity(changed.resourceSites().cycle(site).harvestedCount()));
        assertEquals(ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE,
                ResourceSiteHarvestGoal.current(changed, retained).kind());
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
    }

    @Test void unrelatedLossKeepsPreviousPhysicalWitnessSlot() {
        var progress = new ResourceSiteHarvestProgress(4, 1, -1, 2, 0);
        var afterLoss = progress.accountObservedLoss(3, 2);
        assertEquals(2, afterLoss.completedCropSlots());
        assertEquals(2, afterLoss.nextCropSlotIndex());
        assertEquals(0, afterLoss.lastCompletedCropSlotIndex());
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
