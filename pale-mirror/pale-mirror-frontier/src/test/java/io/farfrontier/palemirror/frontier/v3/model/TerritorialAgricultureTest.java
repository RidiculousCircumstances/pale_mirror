package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TerritorialAgricultureTest {
    @Test void canonicalTerrainMatchesTheNativeQuartGridIncludingNegativeBoundaries() {
        var rules = GrayboxAgriculture.rules();
        assertEquals(4, rules.yield("minecraft:wheat", 120, 0));
        assertEquals(2, rules.yield("minecraft:wheat", 120, -340));
        assertEquals(1, rules.yield("minecraft:wheat", -120, 0));
        for (int x = -245; x < 245; x++) for (int z : List.of(-173, -170, -169, 169, 170, 171))
            assertEquals(rules.biomeAt(Math.floorDiv(x, 4) * 4, Math.floorDiv(z, 4) * 4), rules.biomeAt(x, z));
        assertThrows(IllegalArgumentException.class, () -> new TerritoryAgriculture(rules.defaultBiome(), rules.biomes(), rules.crops(),
                List.of(new TerritoryAgriculture.Region(0, 0, 8, 8, GrayboxAgriculture.FERTILE),
                        new TerritoryAgriculture.Region(4, 4, 12, 12, GrayboxAgriculture.ROCKY))));
        assertThrows(NullPointerException.class, () -> rules.yieldInBiome("minecraft:wheat", "unknown"));
    }
    @Test void mixedFieldGrowsByItsOwnCellEnvironmentWithoutResurrectingPlayerRemovedCrops() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:agronomy"), 125L,
                FrontierRulesets.marketQuarryGraybox()));
        var site = new SubjectId("site:1-wheat-field");
        var cells = List.of(cell(1, 120, 0), cell(2, 120, -340), cell(3, -120, 0));
        var cycle = ResourceFieldCycle.seeded(site, new ResourceFieldLayout(1, 4, cells, List.of()), 1);
        long interval = state.bootstrap().ruleset().cadence().resourceGrowthStageInterval();
        for (int period = 1; period <= 7; period++) cycle = FieldAgronomy.grow(state, cycle, period * interval);
        assertEquals(7, cycle.cell(cells.get(0).id()).growthStage());
        assertEquals(7, cycle.cell(cells.get(1).id()).growthStage());
        assertEquals(3, cycle.cell(cells.get(2).id()).growthStage());
        assertEquals(7, FieldAgronomy.expectedYield(state.bootstrap(), cycle));
        for (int period = 8; period <= 14; period++) cycle = FieldAgronomy.grow(state, cycle, period * interval);
        assertEquals(7, cycle.cell(cells.get(2).id()).growthStage());
        for (var cell : cells) assertEquals(FieldAgronomy.yield(state.bootstrap(), cell.crop()),
                FieldHarvestExtraction.definition(state.bootstrap(), cell.crop()).coldOutput().getFirst().quantity());
        var broken = cycle.cropRemoved(cells.getFirst().id());
        assertEquals(ResourceFieldCycle.Crop.ABSENT, FieldAgronomy.grow(state, broken, 16 * interval).cell(cells.getFirst().id()).crop());
    }
    @Test void restoredTerritorialRulesAndGrowthEventsKeepTheSameBiologicalClock() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:agronomy-recovery"), 125L,
                FrontierRulesets.marketQuarryGraybox()));
        var site = new SubjectId("site:1-wheat-field");
        var preparation = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.planPreparation(state,
                io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.preparation(site, 4000L));
        state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.reducePreparationStarted(state, site,
                (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.reducePrepared(state, site,
                (ResourceSitePrepared) preparation.get(1).payload());
        long interval = state.bootstrap().ruleset().cadence().resourceGrowthStageInterval();
        for (int period = 1; period <= 3; period++) state = grow(state, site, period * interval);
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(state));
        assertEquals(1, recovered.resourceSites().cycle(site).cell(recovered.resourceSites().cycle(site).layout().cells().getFirst().id()).growthStage());
        for (int period = 4; period <= 14; period++) {
            state = grow(state, site, period * interval); recovered = grow(recovered, site, period * interval);
        }
        assertEquals(state.resourceSites(), recovered.resourceSites());
        assertEquals(ResourceSitePhase.READY, recovered.resourceSites().site(site).phase());
    }
    private static FrontierWorldState grow(FrontierWorldState state, SubjectId site, long tick) {
        var events = io.farfrontier.palemirror.frontier.v3.process.ResourceFieldGrowthProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.ResourceFieldGrowthProcess.next(state.resourceSites().site(site), tick));
        for (var event : events) if (event.payload() instanceof ResourceSiteGrowthAdvanced growth)
            state = io.farfrontier.palemirror.frontier.v3.process.ResourceFieldGrowthProcess.reduce(state, site, growth);
        return state;
    }
    private static ResourceFieldLayout.Cell cell(long id, int x, int z) {
        var surface = SurfaceAnchor.at(x, 63, z);
        return new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(id), surface.support().offset(0, 1, 0), surface, surface);
    }
}
