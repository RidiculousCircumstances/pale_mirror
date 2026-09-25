package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceFieldForeignCellObservedTest {
    private static final SubjectId SITE = new SubjectId("site:1-wheat-field");

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
        assertEquals(held, waiting.resourceSites().pendingForeignChange(SITE));
        assertTrue(waiting.resourceSites().hasPendingWorldChange(SITE));
        assertFalse(waiting.resourceSites().hasPendingWorldChange(new SubjectId("site:2-wheat-field")));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceGrowth(waiting, SITE,
                new ResourceSiteGrowthAdvanced(SITE, cycle.epoch(), ready.resourceSites().site(SITE).growthStage())));
        var observed = new ResourceFieldForeignCellObserved(held, blockedSoil(before),
                "minecraft:stone", "minecraft:air");
        assertEquals(observed, roundTrip(observed));
        FrontierWorldState applied = snapshot(ResourceSiteProcess.reduceForeignCellObserved(waiting, SITE, observed));
        assertEquals(ResourceFieldCycle.Soil.OBSTRUCTED, applied.resourceSites().cycle(SITE).cell(first).soil());
        assertEquals(before, applied.resourceSites().cycle(SITE).cell(neighbor));
        assertEquals(0, applied.resourceSites().cycle(SITE).harvestedCount());
        assertEquals(held, applied.resourceSites().pendingForeignChange(SITE));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceForeignChangeAcknowledged(applied, SITE,
                new ResourceFieldForeignChangeAcknowledged(held, before)));
        var acknowledged = new ResourceFieldForeignChangeAcknowledged(held, blockedSoil(before));
        assertEquals(acknowledged, roundTrip(acknowledged));
        FrontierWorldState closed = snapshot(ResourceSiteProcess.reduceForeignChangeAcknowledged(applied, SITE, acknowledged));
        assertNull(closed.resourceSites().pendingForeignChange(SITE));
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
        assertNull(cancelled.resourceSites().pendingForeignChange(SITE));
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
