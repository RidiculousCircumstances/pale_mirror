package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceFieldCellTransitionTest {
    private static final SubjectId SITE = new SubjectId("site:transition-test");
    private static final ResourceFieldLayout.CellId CELL = new ResourceFieldLayout.CellId(19);
    private static final ResourceFieldPhysicalSurface.Condition DIRT = condition(ResourceFieldCycle.Soil.DIRT,
            ResourceFieldCycle.Crop.ABSENT, 0);
    private static final ResourceFieldPhysicalSurface.Condition BARE = condition(ResourceFieldCycle.Soil.FARMLAND,
            ResourceFieldCycle.Crop.ABSENT, 0);
    private static final ResourceFieldPhysicalSurface.Condition PLANTED = condition(ResourceFieldCycle.Soil.FARMLAND,
            ResourceFieldCycle.Crop.GROWING, 0);
    private static final ResourceFieldPhysicalSurface.Condition RIPE = condition(ResourceFieldCycle.Soil.FARMLAND,
            ResourceFieldCycle.Crop.MATURE, 7);

    @Test void tillAndPlantHasAnExactRecoverableSoilThenCropPrefix() {
        var plan = ResourceFieldCellTransition.between(SITE, 1, 4, CELL, DIRT, PLANTED);
        assertEquals(SITE, plan.siteId());
        assertEquals(List.of(ResourceFieldCellTransition.Part.SOIL, ResourceFieldCellTransition.Part.CROP),
                plan.steps().stream().map(ResourceFieldCellTransition.Step::part).toList());
        assertEquals(DIRT, plan.committedPrefix(0));
        assertEquals(BARE, plan.committedPrefix(1));
        assertEquals(PLANTED, plan.committedPrefix(2));
    }

    @Test void damagedSoilClearsCropBeforeChangingSupportAndNeverInventsYield() {
        var plan = ResourceFieldCellTransition.between(SITE, 1, 4, CELL, RIPE, DIRT);
        assertEquals(List.of(ResourceFieldCellTransition.Part.CROP, ResourceFieldCellTransition.Part.SOIL),
                plan.steps().stream().map(ResourceFieldCellTransition.Step::part).toList());
        assertEquals(BARE, plan.committedPrefix(1));
        assertEquals(DIRT, plan.committedPrefix(2));
        assertEquals(List.of(ResourceFieldCellTransition.Part.CROP),
                ResourceFieldCellTransition.between(SITE, 1, 4, CELL, RIPE, PLANTED).steps().stream()
                        .map(ResourceFieldCellTransition.Step::part).toList());
    }

    @Test void actualHarvestAndReplantRetainsTheEmptyCropBoundary() {
        var work = ResourceFieldCellTransition.harvestAndReplant(SITE, 1, 4, CELL, RIPE);
        assertEquals(List.of(ResourceFieldCellTransition.Part.CROP, ResourceFieldCellTransition.Part.CROP),
                work.steps().stream().map(ResourceFieldCellTransition.Step::part).toList());
        assertEquals(RIPE, work.committedPrefix(0));
        assertEquals(BARE, work.committedPrefix(1));
        assertEquals(PLANTED, work.committedPrefix(2));
        assertEquals(true, work.isHarvestAndReplant());
        assertThrows(IllegalArgumentException.class,
                () -> ResourceFieldCellTransition.harvestAndReplant(SITE, 1, 4, CELL, PLANTED));
    }

    @Test void malformedPrefixAndStaleCursorCannotBecomePhysicalAuthority() {
        var one = ResourceFieldCellTransition.between(SITE, 1, 4, CELL, RIPE, BARE);
        assertThrows(IllegalArgumentException.class, () -> one.committedPrefix(2));
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldCellTransition.between(SITE, 1, 4, CELL, BARE, BARE));
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldCellTransition(SITE, 1, 4, CELL, DIRT, PLANTED,
                List.of(new ResourceFieldCellTransition.Step(ResourceFieldCellTransition.Part.CROP, BARE, PLANTED))));
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldCellTransition(SITE, 1, 4, CELL, RIPE, PLANTED,
                List.of(new ResourceFieldCellTransition.Step(ResourceFieldCellTransition.Part.CROP, RIPE, PLANTED),
                        new ResourceFieldCellTransition.Step(ResourceFieldCellTransition.Part.CROP, PLANTED, BARE))),
                "repeated crop writes are valid only for the exact harvest/empty/replant sequence");
    }

    private static ResourceFieldPhysicalSurface.Condition condition(ResourceFieldCycle.Soil soil,
                                                                    ResourceFieldCycle.Crop crop, int stage) {
        return new ResourceFieldPhysicalSurface.Condition(soil, crop, stage);
    }
}
