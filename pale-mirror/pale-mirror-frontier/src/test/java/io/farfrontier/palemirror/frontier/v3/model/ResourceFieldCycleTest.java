package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceFieldCycleTest {
    @Test void replantedCellsGrowDuringTheSameBatchWithoutNewYieldOrRepeatedWork() {
        var geometry = layout(2);
        var first = geometry.cells().getFirst().id();
        var second = geometry.cells().getLast().id();
        var cycle = ResourceFieldCycle.seeded(SITE, geometry, 1);
        for (int stage = 0; stage < 7; stage++) cycle = cycle.advanceGrowthStage();
        cycle = cycle.harvested(first);
        var grown = cycle.advanceGrowthStage();
        assertEquals(1, grown.cell(first).growthStage());
        assertEquals(7, grown.cell(second).growthStage());
        assertTrue(grown.cell(first).accounted());
        assertTrue(grown.cell(first).yielded());
        assertEquals(1, grown.accountedCount());
        assertEquals(1, grown.harvestedCount());
        for (int stage = 1; stage < 7; stage++) grown = grown.advanceGrowthStage();
        assertEquals(ResourceFieldCycle.Crop.MATURE, grown.cell(first).crop());
        assertEquals(1, grown.nextWorkSlot(geometry.cells().getFirst().workstation()).orElseThrow());
        var completed = grown.harvested(second);
        var nextBatch = completed.nextEpoch();
        assertEquals(7, nextBatch.cell(first).growthStage(), "delivery retires work flags, not plant age");
        assertEquals(0, nextBatch.accountedCount());
        assertEquals(0, nextBatch.harvestedCount());
    }

    @Test void exactPhysicalFenceDoesNotSuspendGrowthOfOtherPlants() {
        var geometry = layout(2);
        var first = geometry.cells().getFirst().id();
        var second = geometry.cells().getLast().id();
        var cycle = ResourceFieldCycle.seeded(SITE, geometry, 1);
        var grown = cycle.advanceGrowthStage(java.util.Set.of(first));
        assertEquals(0, grown.cell(first).growthStage());
        assertEquals(1, grown.cell(second).growthStage());
    }

    private static final SubjectId SITE = new SubjectId("site:field-cycle-test");
    private static ResourceFieldLayout layout(int count) {
        var cells = new ArrayList<ResourceFieldLayout.Cell>();
        for (int index = 0; index < count; index++) {
            var support = SurfaceAnchor.at((index % 17) * 2, 63 + index % 3, (index / 17) * 2);
            cells.add(new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(index + 1L),
                    support.support().offset(0, 1, 0), support, support));
        }
        return new ResourceFieldLayout(1, count + 1L, cells, List.of());
    }

    @Test void missingCropAndDamagedSoilKeepFieldIdentityWithoutCreditingWheat() {
        var geometry = layout(3);
        var first = geometry.cells().get(0).id(); var second = geometry.cells().get(1).id(); var third = geometry.cells().get(2).id();
        var cycle = ResourceFieldCycle.unsurveyed(SITE, geometry, 1).prepared(first).prepared(second).prepared(third);
        for (int stage = 0; stage < 7; stage++) cycle = cycle.advanceGrowth(first).advanceGrowth(second).advanceGrowth(third);
        cycle = cycle.cropRemoved(first).soilBecameDirt(second).harvested(third);
        assertFalse(cycle.cycleAccounted(), "lost crop and dirt retain real farmer work");
        cycle = cycle.planted(first).tilled(second).planted(second);
        assertTrue(cycle.cycleAccounted());
        assertEquals(1, cycle.harvestedCount());
        assertEquals(ResourceFieldCycle.Crop.GROWING, cycle.cell(first).crop());
        assertEquals(ResourceFieldCycle.Soil.FARMLAND, cycle.cell(second).soil());
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldCycle.unsurveyed(SITE, geometry, 1).cropRemoved(first),
                "unobserved crop absence cannot be invented");
        var next = cycle.nextEpoch();
        assertEquals(2, next.epoch());
        assertEquals(0, next.harvestedCount());
        assertEquals(ResourceFieldCycle.Crop.GROWING, next.cell(third).crop(), "the previous farmer action planted the successor");
        assertEquals(ResourceFieldCycle.Crop.GROWING, next.cell(second).crop());
    }

    @Test void areaWorkSelectsAnUnaccountedCellAfterABlockedTargetWithoutInventingYield() {
        var geometry = layout(4);
        var cells = geometry.cells();
        var cycle = ResourceFieldCycle.seeded(SITE, geometry, 1);
        for (int stage = 0; stage < 7; stage++) cycle = cycle.advanceGrowthStage();
        var origin = cells.getFirst().workstation();
        assertEquals(0, cycle.nextWorkSlot(origin).orElseThrow());
        cycle = cycle.observedWorkAccess(cells.getFirst().id(), true).skipBlocked(cells.getFirst().id());
        assertEquals(0, cycle.harvestedCount());
        assertEquals(ResourceFieldCycle.Crop.MATURE, cycle.cell(cells.getFirst().id()).crop());
        assertNotEquals(0, cycle.nextWorkSlot(origin).orElseThrow());
        cycle = cycle.worked(cells.get(3).id());
        assertEquals(1, cycle.harvestedCount());
        assertEquals(2, cycle.accountedCount());
        assertFalse(cycle.cell(cells.get(1).id()).accounted());
        assertFalse(cycle.cell(cells.get(2).id()).accounted());
        assertEquals(1, cycle.nextWorkSlotAfter(3).orElseThrow(),
                "continuation wraps through pending CellIds rather than equating count with slot");
        cycle = cycle.worked(cells.get(1).id()).worked(cells.get(2).id());
        assertTrue(cycle.cycleAccounted());
        var next = cycle.nextEpoch().observedWorkAccess(cells.getFirst().id(), false);
        assertEquals(ResourceFieldCycle.WorkOutcome.HARVESTED, next.expectedWorkOutcome(cells.getFirst().id()));
    }

    @Test void workOutcomesExposeTheSameExactCellProjectionForHotAndCold() {
        var geometry = layout(3);
        var first = geometry.cells().get(0).id();
        var second = geometry.cells().get(1).id();
        var third = geometry.cells().get(2).id();
        var cycle = ResourceFieldCycle.seeded(SITE, geometry, 1).cropRemoved(first).soilBecameDirt(second);
        for (int stage = 0; stage < 7; stage++) cycle = cycle.advanceGrowth(third);
        var plant = cycle.physicalWorkTransition(first).orElseThrow();
        var till = cycle.physicalWorkTransition(second).orElseThrow();
        var harvest = cycle.physicalWorkTransition(third).orElseThrow();
        assertEquals(SITE, plant.siteId());
        assertEquals(SITE, till.siteId());
        assertEquals(SITE, harvest.siteId());
        assertEquals(cycle.epoch(), harvest.epoch());
        assertEquals(List.of(ResourceFieldCellTransition.Part.CROP), plant.steps().stream().map(ResourceFieldCellTransition.Step::part).toList());
        assertEquals(List.of(ResourceFieldCellTransition.Part.SOIL, ResourceFieldCellTransition.Part.CROP),
                till.steps().stream().map(ResourceFieldCellTransition.Step::part).toList());
        assertEquals(List.of(ResourceFieldCellTransition.Part.CROP, ResourceFieldCellTransition.Part.CROP),
                harvest.steps().stream().map(ResourceFieldCellTransition.Step::part).toList());
        assertEquals(new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0), harvest.committedPrefix(1));
        assertEquals(ResourceFieldPhysicalSurface.Condition.of(cycle.worked(first).cell(first)), plant.after());
        assertEquals(ResourceFieldPhysicalSurface.Condition.of(cycle.worked(second).cell(second)), till.after());
        assertEquals(ResourceFieldPhysicalSurface.Condition.of(cycle.worked(third).cell(third)), harvest.after());
        assertTrue(cycle.cropObstructed(first).physicalWorkTransition(first).isEmpty());
    }

    @Test void irregularLargeLayoutIsChunkIndexedAndRetainsExactCellStateAcrossRevision() {
        var original = layout(129);
        var changed = ResourceFieldCycle.unsurveyed(SITE, original, 4).prepared(original.cells().get(0).id());
        var retained = original.cells().subList(0, 128);
        var addedSupport = SurfaceAnchor.at(999, 80, 999);
        var added = new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(130),
                addedSupport.support().offset(0, 1, 0), addedSupport, addedSupport);
        var replacement = new ArrayList<>(retained); replacement.add(added);
        var revised = original.revise(2, 131, replacement, List.of());
        assertThrows(IllegalArgumentException.class, () -> changed.revised(revised, Set.of()));
        var next = changed.revised(revised, Set.of(original.cells().getLast().id()));
        assertEquals(changed.cell(original.cells().getFirst().id()), next.cell(original.cells().getFirst().id()));
        assertEquals(ResourceFieldCycle.Soil.UNKNOWN, next.cell(added.id()).soil());
        assertEquals(List.of(added), next.pendingCellsIn(new ResourceFieldLayout.ChunkColumn(62, 62)));
        assertThrows(IllegalArgumentException.class, () -> next.cell(original.cells().getLast().id()));
    }

    @Test void revisionCannotReinterpretAnAccountedWorkerPrefix() {
        var original = layout(2);
        var first = original.cells().getFirst();
        var second = original.cells().getLast();
        var reordered = original.revise(2, original.nextCellId(), List.of(second, first), List.of());
        var working = ResourceFieldCycle.seeded(SITE, original, 1).worked(first.id());
        assertEquals(1, working.accountedPrefixCount());
        assertThrows(IllegalArgumentException.class, () -> working.revised(reordered, Set.of()),
                "a new work order cannot silently assign the old first-cell cursor to another CellId");
        assertEquals(second.id(), ResourceFieldCycle.seeded(SITE, original, 1)
                .revised(reordered, Set.of()).layout().cells().getFirst().id(),
                "the quiescent pure geometry change remains available for an eventual owning transition");
    }

    @Test void restoreRejectsMissingAndForeignCellStates() {
        var geometry = layout(2);
        var cycle = ResourceFieldCycle.unsurveyed(SITE, geometry, 1);
        assertEquals(cycle, ResourceFieldCycle.restore(SITE, geometry, 1, cycle.cellStates()));
        assertNotEquals(cycle, ResourceFieldCycle.restore(new SubjectId("site:foreign"), geometry, 1, cycle.cellStates()));
        var missing = new HashMap<>(cycle.cellStates()); missing.remove(geometry.cells().getFirst().id());
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldCycle.restore(SITE, geometry, 1, missing));
        Map<ResourceFieldLayout.CellId, ResourceFieldCycle.CellState> foreign = new HashMap<>(cycle.cellStates());
        foreign.remove(geometry.cells().getFirst().id());
        foreign.put(new ResourceFieldLayout.CellId(999), cycle.cell(geometry.cells().getFirst().id()));
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldCycle.restore(SITE, geometry, 1, foreign));
    }

    @Test void foreignBlockIsLocalAndClearingItDoesNotInventAPlant() {
        var geometry = layout(1); var id = geometry.cells().getFirst().id();
        var blocked = ResourceFieldCycle.unsurveyed(SITE, geometry, 1).prepared(id).cropObstructed(id).skipBlocked(id);
        assertEquals(0, blocked.harvestedCount());
        assertTrue(blocked.cycleAccounted());
        assertEquals(ResourceFieldCycle.Crop.ABSENT, blocked.obstructionCleared(id).cell(id).crop());
        var next = blocked.obstructionCleared(id).nextEpoch();
        assertEquals(ResourceFieldCycle.Crop.GROWING, next.planted(id).cell(id).crop());
        assertThrows(IllegalArgumentException.class, () -> blocked.harvested(id));
        var supportBlocked = ResourceFieldCycle.unsurveyed(SITE, geometry, 1).prepared(id).soilObstructed(id);
        assertEquals(ResourceFieldCycle.Soil.UNKNOWN, supportBlocked.obstructionCleared(id).cell(id).soil());
        var observedDirt = supportBlocked.obstructionCleared(id).observedBareSoil(id, ResourceFieldCycle.Soil.DIRT);
        assertEquals(ResourceFieldCycle.Crop.GROWING, observedDirt.worked(id).cell(id).crop());
    }

    @Test void cropObstructionOnMissingCropOrDirtRetainsExactRepairDuty() {
        var geometry = layout(1); var id = geometry.cells().getFirst().id();
        var missing = ResourceFieldCycle.seeded(SITE, geometry, 1).cropRemoved(id);
        var blockedFarmland = missing.cropObstructed(id);
        assertEquals(ResourceFieldCycle.Crop.OBSTRUCTED, blockedFarmland.cell(id).crop());
        assertEquals(ResourceFieldCycle.Crop.GROWING,
                blockedFarmland.obstructionCleared(id).worked(id).cell(id).crop());

        var dirt = missing.soilBecameDirt(id);
        var blockedDirt = dirt.cropObstructed(id);
        assertEquals(ResourceFieldCycle.Soil.DIRT, blockedDirt.obstructionCleared(id).cell(id).soil());
        var repaired = blockedDirt.obstructionCleared(id).worked(id);
        assertEquals(ResourceFieldCycle.Soil.FARMLAND, repaired.cell(id).soil());
        assertEquals(ResourceFieldCycle.Crop.GROWING, repaired.cell(id).crop());
        assertEquals(0, repaired.harvestedCount());

        var soilDamagedUnderBlock = blockedFarmland.soilBecameDirt(id);
        assertEquals(ResourceFieldCycle.Crop.OBSTRUCTED, soilDamagedUnderBlock.cell(id).crop(),
                "soil damage must not erase a separately observed foreign block");
        assertEquals(ResourceFieldCycle.Soil.DIRT, soilDamagedUnderBlock.obstructionCleared(id).cell(id).soil());
        assertEquals(ResourceFieldCycle.Crop.OBSTRUCTED, blockedDirt.worked(id).cell(id).crop(),
                "blocked work must leave foreign blocks untouched");
    }

    @Test void farmerVisitAccountsOnlyRipeYieldAndPreservesImmatureGrowthAcrossEpoch() {
        var geometry = layout(3);
        var mature = geometry.cells().get(0).id();
        var missing = geometry.cells().get(1).id();
        var immature = geometry.cells().get(2).id();
        var cycle = ResourceFieldCycle.seeded(SITE, geometry, 1);
        for (int stage = 0; stage < 7; stage++) cycle = cycle.advanceGrowth(mature).advanceGrowth(missing);
        cycle = cycle.advanceGrowth(immature).advanceGrowth(immature).cropRemoved(missing);
        cycle = cycle.worked(mature).worked(missing).worked(immature);
        assertTrue(cycle.cycleAccounted());
        assertEquals(1, cycle.harvestedCount());
        var successor = cycle.nextEpoch();
        assertEquals(2, successor.cell(immature).growthStage());
        assertEquals(ResourceFieldCycle.Crop.GROWING, successor.cell(missing).crop());
        assertEquals(0, successor.harvestedCount());
    }

    @Test void physicalWitnessKeepsExactCellsWithoutBecomingAnotherYieldCounter() {
        var geometry = layout(129);
        var planted = ResourceFieldCycle.seeded(SITE, geometry, 1);
        var first = geometry.cells().getFirst().id();
        for (int stage = 0; stage < 7; stage++) planted = planted.advanceGrowth(first);
        var witness = ResourceFieldPhysicalSurface.fromCycle(planted);
        assertTrue(witness.matches(planted));
        assertFalse(witness.matches(planted.worked(first)), "a planted physical change needs a new exact witness");
        var moved = witness.withCell(first, ResourceFieldPhysicalSurface.Condition.of(planted.worked(first).cell(first)));
        assertTrue(moved.matches(planted.worked(first)));
        assertEquals(witness.cell(geometry.cells().getLast().id()), moved.cell(geometry.cells().getLast().id()));
        var conditions = new HashMap<ResourceFieldLayout.CellId, ResourceFieldPhysicalSurface.Condition>();
        for (var cell : geometry.cells()) conditions.put(cell.id(), witness.cell(cell.id()));
        assertEquals(witness, ResourceFieldPhysicalSurface.restore(geometry, conditions));
        conditions.remove(first);
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldPhysicalSurface.restore(geometry, conditions));
        var foreign = planted.cropObstructed(first);
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldPhysicalSurface.fromCycle(foreign),
                "a foreign block cannot be guessed from a canonical obstruction category");
    }

    @Test void pendingPlayerBreakFencesOnlyItsCellUntilAnExactOutcomeArrives() {
        var geometry = layout(2);
        var first = geometry.cells().getFirst().id(); var second = geometry.cells().getLast().id();
        var seeded = ResourceFieldCycle.seeded(SITE, geometry, 1);
        var pending = new ResourceFieldCycle.PendingPlayerBreak(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000125"), "player:field-break-125",
                ResourceFieldPhysicalSurface.Condition.of(seeded.cell(first)));
        var prepared = seeded.preparePlayerBreak(first, pending);
        assertThrows(IllegalArgumentException.class, () -> seeded.preparePlayerBreak(first, pending).preparePlayerBreak(second, pending));
        var duplicatedOnDisk = new HashMap<>(seeded.cellStates());
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldCycle.restore(SITE, geometry, 1,
                duplicatedOnDisk, java.util.Map.of(first, pending, second, pending)),
                "recovery cannot treat one player action as two independent physical permissions");
        var grown = prepared.advanceGrowthStage();
        assertEquals(0, grown.cell(first).growthStage(), "pending physical action holds only its cell");
        assertEquals(1, grown.cell(second).growthStage());
        assertThrows(IllegalArgumentException.class, () -> grown.worked(first));
        assertThrows(IllegalArgumentException.class, () -> grown.closePlayerBreak(first, "player:wrong"));
        var unchanged = grown.closePlayerBreak(first, pending.actionId());
        assertEquals(1, unchanged.advanceGrowth(first).cell(first).growthStage());
        assertEquals(0, unchanged.pendingPlayerBreaks().size());
    }
}
