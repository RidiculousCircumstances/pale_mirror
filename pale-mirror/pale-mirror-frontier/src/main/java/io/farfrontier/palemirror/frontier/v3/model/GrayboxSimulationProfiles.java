package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture;
import java.util.*;

/** Fresh-world load profiles reuse the same production, economy, movement and custody owners. */
final class GrayboxSimulationProfiles {
    static FrontierRuleset baseline(FrontierRuleset base) {
        return layout("frontier-v3-simulation-baseline-r31", base, FrontierWorldLayout.distant());
    }
    static FrontierRuleset terrain(FrontierRuleset base) {
        var previous = FrontierWorldLayout.distant();
        var layout = new FrontierWorldLayout(previous.bounds(), previous.settlements(), previous.settlementNames(),
                FrontierWorldLayout.Roads.LOCAL_ACCESS, Optional.of(Set.of(0, 1, 3, 5, 7, 10)), FrontierWorldLayout.Terrain.RELIEF);
        return layout("frontier-v3-simulation-terrain-r31", base, layout);
    }
    static FrontierRuleset scale(FrontierRuleset base) {
        return layout("frontier-v3-simulation-scale-r31", base, FrontierWorldLayout.scale());
    }
    static FrontierRuleset scaleFifty(FrontierRuleset base) {
        return layout("frontier-v3-simulation-scale-50-r31", base, FrontierWorldLayout.scaleFifty());
    }
    private static FrontierRuleset layout(String id, FrontierRuleset base, FrontierWorldLayout layout) {
        var fields = new FieldBalance(base.fields().ordinarySide(), base.fields().extractiveSide(),
                Optional.of(GrayboxAgriculture.forLayout(layout)), base.fields().cultivation());
        var forestry = base.forestry();
        if (layout.settlements().size() > 12) {
            var patches = new ArrayList<io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryRules.Patch>();
            int[] forestSectors = {6, 2, 5};
            for (int block = 0; block < layout.settlements().size(); block += 12) for (int i = 0; i < forestSectors.length; i++) {
                int index = block + forestSectors[i]; if (index >= layout.settlements().size()) continue;
                var source = forestry.patches().get(i);
                patches.add(new io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryRules.Patch("pale_mirror:scale-forest-" + index,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId("settlement:" + (index + 1)), source.innerRadius(), source.outerRadius(),
                        source.spacing(), source.densityPermille(), source.salt() + index));
            }
            forestry = new io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryRules(patches, forestry.reserveItems(),
                    forestry.resumeBelowItems(), forestry.workersPerSettlement(), forestry.cargoItems(), forestry.reviewTicks(),
                    forestry.tool(), forestry.harvests(), forestry.starterEquipment());
        }
        return new FrontierRuleset(id, base.schemaVersion(), base.cadence(), base.spatial(), base.rates(), base.facilityCapacity(),
                base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(),
                base.execution(), base.production(), fields, base.tools(), layout, forestry);
    }
    private GrayboxSimulationProfiles() { }
}
