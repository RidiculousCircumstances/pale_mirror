package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture;
import java.util.*;

/** Fresh-world load profiles reuse the same production, economy, movement and custody owners. */
final class GrayboxSimulationProfiles {
    static FrontierRuleset baseline(FrontierRuleset base) {
        return layout("frontier-v3-simulation-baseline-r31", base, FrontierWorldLayout.distant());
    }
    static FrontierRuleset scale(FrontierRuleset base) {
        return layout("frontier-v3-simulation-scale-r31", base, FrontierWorldLayout.scale());
    }
    private static FrontierRuleset layout(String id, FrontierRuleset base, FrontierWorldLayout layout) {
        var fields = new FieldBalance(base.fields().ordinarySide(), base.fields().extractiveSide(),
                Optional.of(GrayboxAgriculture.forLayout(layout)), base.fields().cultivation());
        return new FrontierRuleset(id, base.schemaVersion(), base.cadence(), base.spatial(), base.rates(), base.facilityCapacity(),
                base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(),
                base.execution(), base.production(), fields, base.tools(), layout, base.forestry());
    }
    private GrayboxSimulationProfiles() { }
}
