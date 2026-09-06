package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementFacadeAccessTest {
    @Test void highThresholdDescendsUsingExactGroundInsteadOfAStoneCauseway() {
        var publicStreet = new LinearFeaturePlan("street", LinearFeatureKind.STREET,
                List.of(new VisualPoint(6, 65, -2), new VisualPoint(6, 65, 2)), 5, true);
        var access = new LinearFeaturePlan("access_0", LinearFeatureKind.FOOTPATH,
                List.of(new VisualPoint(0, 67, 0), new VisualPoint(6, 67, 0)), 3, true);
        // The coarse survey inherited the high doorway shelf. Exact columns outside
        // the facade are two blocks lower, as in the reported town-hall regression.
        var snapshot = new SettlementTerrainSnapshot(new VisualPoint(0, 68, 0),
                Map.of(SettlementTerrainSnapshot.key(0, 0), 68),
                (x, z) -> x == 0 ? 68 : 66, (x, z) -> false);

        var resolved = SettlementStreetNetworkPlanner.resolve(List.of(publicStreet, access), snapshot)
                .stream().filter(value -> value.id().equals("access_0")).findFirst().orElseThrow();

        assertEquals(List.of(67, 66, 65, 65, 65, 65, 65),
                resolved.nodes().stream().map(VisualPoint::y).toList(),
                "only the doorway cell may retain the high threshold; the approach must meet natural grade");
    }
}
