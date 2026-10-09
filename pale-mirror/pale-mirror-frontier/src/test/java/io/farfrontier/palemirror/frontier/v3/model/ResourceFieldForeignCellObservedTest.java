package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceFieldForeignCellObservedTest {
    private static final SubjectId SITE = new SubjectId("site:1-wheat-field");

    @Test void twoHeldCellsDoNotParkTheirNeighboursAndKeepExactSnapshotOwners() {
        var initial = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        var field = initial.resourceSites().cycle(SITE);
        var cells = new java.util.LinkedHashMap<ResourceFieldLayout.CellId, ResourceFieldCycle.CellState>();
        for (var cell : field.layout().cells()) cells.put(cell.id(), new ResourceFieldCycle.CellState(
                ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 6, false, false));
        field = ResourceFieldCycle.restore(SITE, field.layout(), field.epoch(), cells);
        var ready = initial.withResourceSites(initial.resourceSites().replace(initial.resourceSites().site(SITE).withPlantReadiness(field), field));
        var a = field.layout().cells().get(0).id(); var b = field.layout().cells().get(1).id(); var c = field.layout().cells().get(2).id();
        var first = new ResourceFieldForeignChangeHeld(SITE, field.epoch(), field.layout().revision(), a, field.cell(a), "world:cell-a");
        var second = new ResourceFieldForeignChangeHeld(SITE, field.epoch(), field.layout().revision(), b, field.cell(b), "world:cell-b");
        var waiting = snapshot(ResourceSiteProcess.reduceForeignChangeHeld(
                ResourceSiteProcess.reduceForeignChangeHeld(ready, SITE, first), SITE, second));
        assertEquals(2, waiting.resourceSites().pendingForeignChanges().size());
        var advanced = ResourceSiteProcess.reduceGrowth(waiting, SITE,
                new ResourceSiteGrowthAdvanced(SITE, field.epoch(), waiting.resourceSites().site(SITE).growthStage()));
        assertEquals(6, advanced.resourceSites().cycle(SITE).cell(a).growthStage());
        assertEquals(6, advanced.resourceSites().cycle(SITE).cell(b).growthStage());
        assertEquals(7, advanced.resourceSites().cycle(SITE).cell(c).growthStage());
        var player = new ResourceFieldPlayerBreakPrepared(SITE, field.epoch(), field.layout().revision(), c,
                ResourceFieldPhysicalSurface.Condition.of(advanced.resourceSites().cycle(SITE).cell(c)),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000080"), "action:independent-cell-c");
        var withPlayer = snapshot(ResourceSiteProcess.reducePlayerBreakPrepared(advanced, SITE, player));
        assertTrue(withPlayer.resourceSites().cycle(SITE).pendingPlayerBreaks().containsKey(c));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceForeignChangeHeld(waiting, SITE, first));
        var changed = ResourceSiteProcess.reduceForeignCellObserved(withPlayer, SITE,
                new ResourceFieldForeignCellObserved(first, blockedSoil(first.before()), "minecraft:stone", "minecraft:air"));
        var closed = snapshot(ResourceSiteProcess.reduceForeignChangeAcknowledged(changed, SITE,
                new ResourceFieldForeignChangeAcknowledged(first, changed.resourceSites().cycle(SITE).cell(a))));
        assertNull(closed.resourceSites().pendingForeignChange(SITE, a));
        assertEquals(second, closed.resourceSites().pendingForeignChange(SITE, b));
        assertEquals(7, closed.resourceSites().cycle(SITE).cell(c).growthStage());
        assertEquals(0, closed.resourceSites().cycle(SITE).harvestedCount());
    }

    @Test void foreignCropObservationPreservesIndependentBlockedHeadroomAcrossWalAndSnapshot() {
        FrontierWorldState ready = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        ResourceFieldCycle cycle = ready.resourceSites().cycle(SITE);
        var id = cycle.layout().cells().getFirst().id();
        ready = ready.withResourceSites(ready.resourceSites().replace(
                ready.resourceSites().site(SITE), cycle.observedWorkAccess(id, true)));
        cycle = ready.resourceSites().cycle(SITE);
        var held = new ResourceFieldForeignChangeHeld(SITE, cycle.epoch(), cycle.layout().revision(), id,
                cycle.cell(id), "world:crop-blocked-with-headroom-blocked");
        assertEquals(held, roundTrip(held));
        var after = new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.OBSTRUCTED, 0, false, false, true);
        var observed = new ResourceFieldForeignCellObserved(held, after, "minecraft:farmland", "minecraft:stone");
        assertEquals(observed, roundTrip(observed));
        FrontierWorldState waiting = ResourceSiteProcess.reduceForeignChangeHeld(ready, SITE, held);
        FrontierWorldState heldState = waiting;
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceForeignCellObserved(heldState, SITE,
                new ResourceFieldForeignCellObserved(held,
                        new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                                ResourceFieldCycle.Crop.OBSTRUCTED, 0, false, false, false),
                        "minecraft:farmland", "minecraft:stone")));
        FrontierWorldState changed = ResourceSiteProcess.reduceForeignCellObserved(waiting, SITE, observed);
        assertTrue(changed.resourceSites().cycle(SITE).cell(id).workAccessBlocked());
        assertEquals(changed, snapshot(changed));
    }

    @Test void foreignCropBeforePhysicalWorkEffectClosesOnlyTheLostCell() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        FrontierWorldState state = hot.state();
        ResourceSiteHarvestJob job = hot.job();
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            state = ResourceSiteHarvestProcessTest.inspectGoal(state, SITE, job, hot.lease().id());
        state = ResourceSiteHarvestProcessTest.completeLabourHot(state, SITE, hot.lease().id());
        state = ResourceSiteHarvestProcess.reduceCropPrepared(state, SITE,
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation()));
        ResourceFieldCycle cycle = state.resourceSites().cycle(SITE);
        ResourceFieldLayout.CellId cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        var before = cycle.cell(cell);
        var held = new ResourceFieldForeignChangeHeld(SITE, cycle.epoch(), cycle.layout().revision(), cell,
                before, "world:prepared-crop-obstructed");
        FrontierWorldState waiting = ResourceSiteProcess.reduceForeignChangeHeld(state, SITE, held);
        var obstructed = new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.OBSTRUCTED, 0, false, false);
        FrontierWorldState changed = ResourceSiteProcess.reduceForeignCellObserved(waiting, SITE,
                new ResourceFieldForeignCellObserved(held, obstructed, "minecraft:farmland", "minecraft:stone"));
        ResourceSiteHarvestJob retained = (ResourceSiteHarvestJob) changed.resourceSites().site(SITE).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertEquals(job.id(), retained.id());
        assertEquals(job.workerId(), retained.workerId());
        assertFalse(retained.progress().hasPendingCrop());
        assertEquals(job.progress().completedCropSlots() + 1, retained.progress().completedCropSlots());
        assertFalse(changed.resourceSites().cycle(SITE).cell(cell).accounted());
        assertFalse(changed.resourceSites().cycle(SITE).cell(cell).yielded());
        changed = ResourceSiteProcess.reduceForeignChangeAcknowledged(changed, SITE,
                new ResourceFieldForeignChangeAcknowledged(held, changed.resourceSites().cycle(SITE).cell(cell)));
        FrontierWorldState acknowledged = changed;
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceForeignChangeAcknowledged(
                acknowledged, SITE, new ResourceFieldForeignChangeAcknowledged(held, obstructed)));
        assertEquals(changed, snapshot(changed));
    }

    @Test void foreignSupportIsLocalHeldAndSurvivesBothRecoveryWindows() {
        FrontierWorldState ready = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        ResourceFieldCycle cycle = ready.resourceSites().cycle(SITE);
        var first = cycle.layout().cells().getFirst().id();
        var neighbor = cycle.layout().cells().get(1).id();
        var before = cycle.cell(first);
        var held = new ResourceFieldForeignChangeHeld(SITE, cycle.epoch(), cycle.layout().revision(),
                first, before, "world:foreign-support-1");
        assertEquals(held, roundTrip(held));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceForeignCellObserved(ready, SITE,
                new ResourceFieldForeignCellObserved(held, blockedSoil(before), "minecraft:stone", "minecraft:air")));
        FrontierWorldState waiting = snapshot(ResourceSiteProcess.reduceForeignChangeHeld(ready, SITE, held));
        assertEquals(held, waiting.resourceSites().pendingForeignChange(SITE, first));
        assertTrue(waiting.resourceSites().hasPendingWorldChange(SITE));
        assertFalse(waiting.resourceSites().hasPendingWorldChange(new SubjectId("site:2-wheat-field")));
        assertEquals(before, ResourceSiteProcess.reduceGrowth(waiting, SITE,
                new ResourceSiteGrowthAdvanced(SITE, cycle.epoch(), ready.resourceSites().site(SITE).growthStage()))
                .resourceSites().cycle(SITE).cell(first), "growth protects the exact cell, not its entire site");
        var observed = new ResourceFieldForeignCellObserved(held, blockedSoil(before),
                "minecraft:stone", "minecraft:air");
        assertEquals(observed, roundTrip(observed));
        FrontierWorldState applied = snapshot(ResourceSiteProcess.reduceForeignCellObserved(waiting, SITE, observed));
        assertEquals(ResourceFieldCycle.Soil.OBSTRUCTED, applied.resourceSites().cycle(SITE).cell(first).soil());
        assertEquals(before, applied.resourceSites().cycle(SITE).cell(neighbor));
        assertEquals(0, applied.resourceSites().cycle(SITE).harvestedCount());
        assertEquals(held, applied.resourceSites().pendingForeignChange(SITE, first));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceForeignChangeAcknowledged(applied, SITE,
                new ResourceFieldForeignChangeAcknowledged(held, before)));
        var acknowledged = new ResourceFieldForeignChangeAcknowledged(held, blockedSoil(before));
        assertEquals(acknowledged, roundTrip(acknowledged));
        FrontierWorldState closed = snapshot(ResourceSiteProcess.reduceForeignChangeAcknowledged(applied, SITE, acknowledged));
        assertNull(closed.resourceSites().pendingForeignChange(SITE, first));
        assertEquals(ResourceFieldCycle.Soil.OBSTRUCTED, closed.resourceSites().cycle(SITE).cell(first).soil());
    }

    @Test void exactObservedClearanceAndCancelledWriteDoNotInventHarvest() {
        FrontierWorldState ready = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        ResourceFieldCycle cycle = ready.resourceSites().cycle(SITE);
        var first = cycle.layout().cells().getFirst().id();
        var initial = new ResourceFieldForeignChangeHeld(SITE, cycle.epoch(), cycle.layout().revision(),
                first, cycle.cell(first), "world:foreign-crop-1");
        FrontierWorldState held = ResourceSiteProcess.reduceForeignChangeHeld(ready, SITE, initial);
        FrontierWorldState cancelled = ResourceSiteProcess.reduceForeignChangeAcknowledged(held, SITE,
                new ResourceFieldForeignChangeAcknowledged(initial, cycle.cell(first)));
        assertEquals(cycle, cancelled.resourceSites().cycle(SITE));
        assertNull(cancelled.resourceSites().pendingForeignChange(SITE, first));
        var obstructed = new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.OBSTRUCTED, 0, false, false);
        FrontierWorldState changed = ResourceSiteProcess.reduceForeignCellObserved(held, SITE,
                new ResourceFieldForeignCellObserved(initial, obstructed, "minecraft:farmland", "minecraft:stone"));
        changed = ResourceSiteProcess.reduceForeignChangeAcknowledged(changed, SITE,
                new ResourceFieldForeignChangeAcknowledged(initial, obstructed));
        var clear = new ResourceFieldForeignChangeHeld(SITE, cycle.epoch(), cycle.layout().revision(),
                first, obstructed, "world:clear-crop-1");
        FrontierWorldState clearing = snapshot(ResourceSiteProcess.reduceForeignChangeHeld(changed, SITE, clear));
        var bare = new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0, false, false);
        FrontierWorldState cleared = ResourceSiteProcess.reduceForeignCellObserved(clearing, SITE,
                new ResourceFieldForeignCellObserved(clear, bare, "minecraft:farmland", "minecraft:air"));
        cleared = snapshot(ResourceSiteProcess.reduceForeignChangeAcknowledged(cleared, SITE,
                new ResourceFieldForeignChangeAcknowledged(clear, bare)));
        assertEquals(bare, cleared.resourceSites().cycle(SITE).cell(first));
        assertEquals(0, cleared.resourceSites().cycle(SITE).harvestedCount());
        assertEquals(cycle.cell(cycle.layout().cells().get(1).id()),
                cleared.resourceSites().cycle(SITE).cell(cycle.layout().cells().get(1).id()));
    }

    private static ResourceFieldCycle.CellState blockedSoil(ResourceFieldCycle.CellState before) {
        return new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.OBSTRUCTED,
                ResourceFieldCycle.Crop.OBSTRUCTED, 0, before.accounted(), before.yielded());
    }

    private static FrontierWorldState snapshot(FrontierWorldState state) {
        var codec = new FrontierWorldStateCodec();
        return codec.decode(codec.encode(state));
    }

    @SuppressWarnings("unchecked")
    private static <T extends io.farfrontier.palemirror.frontier.v3.api.FrontierPayload> T roundTrip(T payload) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        return (T) codecs.decode(payload.type(), codecs.encode(payload));
    }
}
