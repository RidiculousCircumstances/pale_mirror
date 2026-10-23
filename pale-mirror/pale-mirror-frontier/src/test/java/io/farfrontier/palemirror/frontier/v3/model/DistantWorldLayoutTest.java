package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistantWorldLayoutTest {
    @Test void distantWorldRetainsIrregularDistancesAndTerritorialConditionsThroughRecovery() {
        var rules = FrontierRulesets.installed("frontier-v3-quarry-graybox-r26");
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:distant-recovery"), 127, rules);
        var compact = FrontierWorldLayout.compact().settlements();
        var distant = rules.worldLayout().settlements();
        var ratios = new java.util.HashSet<Long>();
        for (int i = 0; i < compact.size(); i++) {
            var old = compact.get(i); var town = distant.get(i);
            assertEquals(io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture.rules().biomeAt(old.x(), old.z()),
                    rules.fields().agriculture().orElseThrow().biomeAt(town.x(), town.z()));
            for (int j = i + 1; j < compact.size(); j++) {
                double before = Math.hypot(old.x() - compact.get(j).x(), old.z() - compact.get(j).z());
                double after = Math.hypot(town.x() - distant.get(j).x(), town.z() - distant.get(j).z());
                assertTrue(after / before >= 3 && after / before <= 5, "pair " + i + "/" + j);
                ratios.add(Math.round(100 * after / before));
            }
        }
        assertTrue(ratios.size() > 10);
        var state = FrontierWorldState.initial(bootstrap); var codec = new FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(state));
        assertEquals(state, restored);
        var fabric = FrontierGrayboxPlan.compileInitialSettlementFabric(bootstrap);
        assertTrue(fabric.cells().size() > 65536, "distant roads exceed the former compact-world envelope");
        assertEquals(FrontierGrayboxPlan.compileStableStructuralBaseline(state).cells(),
                FrontierGrayboxPlan.compileStableStructuralBaseline(restored).cells());
    }
}
